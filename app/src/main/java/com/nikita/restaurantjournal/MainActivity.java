package com.nikita.restaurantjournal;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private final int GREEN=Color.rgb(22,74,64), INK=Color.rgb(32,46,40), CREAM=Color.rgb(247,244,237);
    private SQLiteDatabase db;
    private LinearLayout list;
    private TextView summary, lastBackup;
    private EditText search;
    private AlertDialog editDialog;
    private EditText draftName, draftComment;
    private SeekBar draftGrade;
    private long editingId;
    private String originalDraftName, originalDraftComment;
    private int originalDraftGrade;
    private AlertDialog discardDialog;
    private String draftPlaceId = "", pendingPlaceId = "";
    private int sort=0;
    private String pendingRestoreUri;
    private AlertDialog restoreDialog;
    private static final int EXPORT=41, IMPORT=42, DISCOVER=43;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db=new SQLiteOpenHelper(this,"restaurants.db",null,2) {
            public void onCreate(SQLiteDatabase d) { d.execSQL("CREATE TABLE places (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, grade INTEGER NOT NULL CHECK(grade BETWEEN 0 AND 10), comment TEXT NOT NULL, updated INTEGER NOT NULL, google_place_id TEXT)"); }
            public void onUpgrade(SQLiteDatabase d,int old,int next) { if(old<2)d.execSQL("ALTER TABLE places ADD COLUMN google_place_id TEXT"); }
        }.getWritableDatabase();
        LinearLayout root=column(); root.setBackgroundColor(CREAM);
        root.setPadding(dp(22),dp(12),dp(22),dp(12));
        root.setOnApplyWindowInsetsListener((v,i)->{ v.setPadding(dp(22),i.getSystemWindowInsetTop()+dp(12),dp(22),i.getSystemWindowInsetBottom()+dp(12)); return i; });
        setContentView(root);
        TextView eyebrow=text("YOUR PERSONAL FOOD JOURNAL",11,GREEN); eyebrow.setLetterSpacing(.12f); root.addView(eyebrow);
        LinearLayout heading=new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Table Notes",32,INK); title.setTypeface(null,Typeface.BOLD); heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button menu=button("⋮"); menu.setContentDescription("Backup and restore menu"); heading.addView(menu); menu.setOnClickListener(v->backupMenu(v)); root.addView(heading);
        summary=text("",14,GREEN); summary.setPadding(0,dp(8),0,dp(16)); root.addView(summary);
        lastBackup=text("",12,GREEN); root.addView(lastBackup);
        Button discover=button("Discover restaurants · Google Maps");
        root.addView(discover,new LinearLayout.LayoutParams(-1,dp(52)));
        discover.setOnClickListener(v->startActivityForResult(new Intent(this,DiscoverActivity.class),DISCOVER));
        search=new EditText(this); search.setSingleLine(true); search.setHint("Search restaurants or comments"); search.setTextSize(15); root.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
        Spinner order=new Spinner(this); order.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Recently edited","Highest grade","Lowest grade","Name A–Z"})); root.addView(order);
        order.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onItemSelected(AdapterView<?> p,View v,int pos,long id){sort=pos;refresh();} public void onNothingSelected(AdapterView<?> p){} });
        ScrollView scroll=new ScrollView(this); list=column(); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button add=button("+  Add a restaurant"); add.setTextColor(Color.WHITE); add.setBackground(shape(GREEN,18)); root.addView(add,new LinearLayout.LayoutParams(-1,dp(56))); add.setOnClickListener(v->editor(-1,"",8,""));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh();}public void afterTextChanged(Editable e){} });
        if(state!=null){sort=state.getInt("sort",0); order.setSelection(sort); search.setText(state.getString("search",""));}
        refresh();
        if(state!=null && state.getBoolean("editing",false)) {
            pendingPlaceId=state.getString("draftPlaceId","");
            editor(state.getLong("editingId"),state.getString("draftName",""),state.getInt("draftGrade",8),state.getString("draftComment",""));
            originalDraftName=state.getString("originalDraftName",originalDraftName);
            originalDraftComment=state.getString("originalDraftComment",originalDraftComment);
            originalDraftGrade=state.getInt("originalDraftGrade",originalDraftGrade);
            if(state.getBoolean("discard_open"))requestDiscard();
        }
        if(state!=null && state.getString("pendingRestoreUri")!=null) {
            pendingRestoreUri=state.getString("pendingRestoreUri");
            restoreBackup(new Intent().setData(android.net.Uri.parse(pendingRestoreUri)));
        }
        DailyDiscoveryJob.ensureScheduled(this);
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b); b.putString("pendingRestoreUri",pendingRestoreUri); b.putInt("sort",sort); b.putString("search",search.getText().toString());if(editDialog!=null&&editDialog.isShowing()){b.putBoolean("editing",true);b.putLong("editingId",editingId);b.putString("draftName",draftName.getText().toString());b.putString("draftComment",draftComment.getText().toString());b.putInt("draftGrade",draftGrade.getProgress());b.putString("draftPlaceId",draftPlaceId);b.putString("originalDraftName",originalDraftName);b.putString("originalDraftComment",originalDraftComment);b.putInt("originalDraftGrade",originalDraftGrade);b.putBoolean("discard_open",discardDialog!=null&&discardDialog.isShowing());}}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(GREEN);return b;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void refresh(){
        if(list==null)return; list.removeAllViews();
        long backup=getSharedPreferences("journal",MODE_PRIVATE).getLong("last_backup",0);
        lastBackup.setText("Last backup: "+(backup==0?"Never":java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new Date(backup))));
        try(Cursor c=db.rawQuery("SELECT COUNT(*), AVG(grade) FROM places",null)){c.moveToFirst();int n=c.getInt(0);summary.setText(n==0?"Good meals deserve a little note.":n+" restaurant"+(n==1?"":"s")+"  ·  "+String.format(Locale.getDefault(),"%.1f",c.getDouble(1))+" / 10 average");}
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        String order=new String[]{"updated DESC, id DESC","grade DESC, name COLLATE NOCASE","grade ASC, name COLLATE NOCASE","name COLLATE NOCASE"}[sort];
        int count=0;
        try(Cursor c=db.rawQuery("SELECT id,name,grade,comment,google_place_id FROM places ORDER BY "+order,null)){
            while(c.moveToNext()){
                long id=c.getLong(0);String name=c.getString(1),comment=c.getString(3);int grade=c.getInt(2);
                if(!name.toLowerCase(Locale.ROOT).contains(query)&&!comment.toLowerCase(Locale.ROOT).contains(query))continue;
                count++; LinearLayout card=column();card.setPadding(dp(18),dp(16),dp(18),dp(16));card.setBackground(shape(Color.WHITE,18));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(6),0,dp(6));list.addView(card,cp);
                TextView nameView=text(name,21,INK);nameView.setTypeface(null,Typeface.BOLD);card.addView(nameView);
                TextView score=text(grade+" / 10",19,GREEN);score.setPadding(0,dp(6),0,dp(6));card.addView(score);
                TextView note=text(comment.isEmpty()?"Tap to add a comment":comment,15,Color.rgb(94,101,92));note.setMaxLines(2);note.setEllipsize(TextUtils.TruncateAt.END);card.addView(note);
                String placeId=c.getString(4);
                if(placeId!=null&&!placeId.isEmpty()){Button map=button("View on Google Maps");card.addView(map);map.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query="+android.net.Uri.encode(name)+"&query_place_id="+android.net.Uri.encode(placeId))));}catch(ActivityNotFoundException error){toast("No app is available to open Google Maps.");}});}
                card.setFocusable(true);card.setContentDescription(name+", "+grade+" out of 10. "+comment);card.setOnClickListener(v->detail(id,name,grade,comment));
            }
        }
        if(count==0){TextView empty=text(query.isEmpty()?"Your next great meal starts here.\n\nAdd your first restaurant and give it a grade out of 10.":"No matches. Try another name or comment.",19,INK);empty.setPadding(dp(8),dp(48),dp(8),dp(48));empty.setGravity(Gravity.CENTER);list.addView(empty);}
    }
    private void detail(long id,String name,int grade,String comment){new AlertDialog.Builder(this).setTitle(name+" · "+grade+" / 10").setMessage(comment.isEmpty()?"No comment yet.":comment).setPositiveButton("Edit",(d,w)->editor(id,name,grade,comment)).setNeutralButton("Close",null).setNegativeButton("Delete",(d,w)->new AlertDialog.Builder(this).setTitle("Delete "+name+"?").setMessage("This removes its grade and comment from your phone.").setNegativeButton("Keep",null).setPositiveButton("Delete",(a,b)->{db.delete("places","id=?",new String[]{String.valueOf(id)});refresh();}).show()).show();}
    private void editor(long id,String original,int initial,String oldComment){
        final String linkedPlaceId=id<0?pendingPlaceId:"";pendingPlaceId="";
        LinearLayout form=column();form.setPadding(dp(24),dp(12),dp(24),0);
        EditText name=new EditText(this);name.setHint("Restaurant name");name.setSingleLine(true);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});name.setText(original);form.addView(name);
        TextView label=text("Grade: "+initial+" / 10",18,GREEN);label.setPadding(0,dp(20),0,dp(8));form.addView(label);
        SeekBar grade=new SeekBar(this);grade.setMax(10);grade.setProgress(initial);grade.setContentDescription("Grade out of 10");form.addView(grade,new LinearLayout.LayoutParams(-1,dp(48)));
        grade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){label.setText("Grade: "+p+" / 10");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){} });
        EditText comment=new EditText(this);comment.setHint("What did you think?");comment.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);comment.setMinLines(3);comment.setMaxLines(6);comment.setGravity(Gravity.TOP);comment.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});comment.setText(oldComment);form.addView(comment);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(id<0?"Add restaurant":"Edit restaurant").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        originalDraftName=original;originalDraftComment=oldComment;originalDraftGrade=initial;
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)requestDiscard();return true;}return false;});
        editDialog=dialog;draftName=name;draftComment=comment;draftGrade=grade;editingId=id;draftPlaceId=linkedPlaceId;
        dialog.setOnShowListener(v->{dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(b->requestDiscard());dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("Enter a restaurant name");return;}ContentValues values=new ContentValues();values.put("name",n);values.put("grade",grade.getProgress());values.put("comment",comment.getText().toString().trim());values.put("updated",System.currentTimeMillis());if(id<0&&!linkedPlaceId.isEmpty())values.put("google_place_id",linkedPlaceId);try{if(id<0)db.insertOrThrow("places",null,values);else db.update("places",values,"id=?",new String[]{String.valueOf(id)});dialog.dismiss();refresh();}catch(Exception e){toast("Couldn't save. Please try again.");}});});dialog.show();
    }
    private void requestDiscard() {
        if(discardDialog!=null&&discardDialog.isShowing())return;
        boolean changed=!draftName.getText().toString().equals(originalDraftName)
                || !draftComment.getText().toString().equals(originalDraftComment)
                || draftGrade.getProgress()!=originalDraftGrade;
        if(!changed){editDialog.dismiss();return;}
        discardDialog=new AlertDialog.Builder(this).setTitle("Discard changes?")
                .setMessage("Your unsaved name, grade and comment will be discarded.")
                .setNegativeButton("Keep editing",null)
                .setPositiveButton("Discard",(d,w)->editDialog.dismiss()).show();
    }
    private void backupMenu(View anchor){PopupMenu m=new PopupMenu(this,anchor);m.getMenu().add("Export backup");m.getMenu().add("Restore backup");m.setOnMenuItemClickListener(item->{if(item.getTitle().equals("Export backup")){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"table-notes-backup.json");startActivityForResult(i,EXPORT);}else{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,IMPORT);}return true;});m.show();}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==DISCOVER) {
            if(result==RESULT_OK && data!=null) {
                pendingPlaceId=data.getStringExtra("google_place_id");
                if(pendingPlaceId==null || pendingPlaceId.length()>1024)pendingPlaceId="";
                editor(-1,"",8,"");
            }
            return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==EXPORT)exportBackup(data);
        if(request==IMPORT) {
            if(restoreDialog!=null)restoreDialog.dismiss();
            clearPendingRestore();
            try {if((data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            catch(SecurityException unsupported) { /* The current temporary read grant still survives rotation. */ }
            pendingRestoreUri=data.getData().toString();
            restoreBackup(data);
        }
    }
    private void exportBackup(Intent data) {
        try {
            List<BackupCodec.Entry> rows=new ArrayList<>();
            try(Cursor cursor=db.rawQuery("SELECT name,grade,comment,updated,google_place_id FROM places ORDER BY id",null)) {
                while(cursor.moveToNext())rows.add(new BackupCodec.Entry(cursor.getString(0),cursor.getInt(1),cursor.getString(2),cursor.getLong(3),cursor.isNull(4)?"":cursor.getString(4)));
            }
            String json=BackupCodec.encode(rows);
            try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")) {
                if(out==null)throw new IOException();
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            getSharedPreferences("journal",MODE_PRIVATE).edit().putLong("last_backup",System.currentTimeMillis()).apply();
            refresh();
            toast("Backup saved");
        }catch(Exception error){toast("Couldn't export the backup.");}
    }
    private void restoreBackup(Intent data) {
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=getContentResolver().openInputStream(data.getData())) {
                if(in==null)throw new IOException();
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){out.write(buffer,0,count);if(out.size()>5_000_000)throw new IOException();}
            }
            // Parse every record before offering to replace the database.
            List<BackupCodec.Entry> entries=BackupCodec.decode(out.toString("UTF-8"));
            restoreDialog=new AlertDialog.Builder(this).setTitle("Restore "+entries.size()+" restaurants?")
                    .setMessage("This replaces your current list. Export it first if you want to keep it.")
                    .setNegativeButton("Cancel",(dialog,which)->clearPendingRestore()).setPositiveButton("Replace list",(dialog,which)->{
                        clearPendingRestore();
                        db.beginTransaction();
                        try {
                            db.delete("places",null,null);
                            for(BackupCodec.Entry entry:entries) {
                                ContentValues values=new ContentValues();values.put("name",entry.name);values.put("grade",entry.grade);
                                values.put("comment",entry.comment);values.put("updated",entry.updated);
                                if(!entry.placeId.isEmpty())values.put("google_place_id",entry.placeId);
                                db.insertOrThrow("places",null,values);
                            }
                            db.setTransactionSuccessful();toast("Backup restored");
                        }catch(Exception error){toast("Restore failed; your list was kept.");}
                        finally{db.endTransaction();}
                        refresh();
                    }).create();
            restoreDialog.setOnCancelListener(dialog->clearPendingRestore());
            restoreDialog.show();
        }catch(IOException | SecurityException error){clearPendingRestore();toast("Couldn't read the backup. Select the file again.");}
        catch(Exception error){clearPendingRestore();toast("This isn't a valid Table Notes backup.");}
    }
    private void clearPendingRestore() {
        if(pendingRestoreUri!=null) {
            try {getContentResolver().releasePersistableUriPermission(android.net.Uri.parse(pendingRestoreUri),Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            catch(SecurityException unsupported) {}
        }
        pendingRestoreUri=null;
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){if(isFinishing())clearPendingRestore();if(restoreDialog!=null)restoreDialog.dismiss();if(discardDialog!=null)discardDialog.dismiss();if(editDialog!=null)editDialog.dismiss();super.onDestroy();db.close();}
}

