package com.nikita.restaurantjournal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.libraries.places.api.model.Place;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.*;

public class DiscoverActivity extends Activity {
    private static final int GREEN = Color.rgb(22,74,64), INK = Color.rgb(32,46,40);
    private static final int LOCATION_PERMISSION = 60;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, areaLabel;
    private LinearLayout results;
    private Button update;
    private CancellationTokenSource request;
    private CancellationSignal locationRequest;
    private LocationListener legacyLocationListener;
    private LocationManager locationManager;
    private boolean findingLocation;
    private int requestSequence;
    private AlertDialog setupDialog;
    private final List<AlertDialog> dialogs = new ArrayList<>();
    // Session-only provider content: never written to preferences or saved state.
    private static List<Place> sessionPlaces;
    private static Set<String> sessionNewIds = Collections.emptySet();
    private static String sessionRevision;
    private boolean initialLoadPending;

    private AlertDialog track(AlertDialog dialog) { dialogs.add(dialog); return dialog; }
    private void clearResults() {
        sessionPlaces=null; sessionNewIds=Collections.emptySet(); sessionRevision=null;
    }


    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(247,244,237));
        root.setPadding(dp(22),dp(12),dp(22),dp(12));
        root.setOnApplyWindowInsetsListener((v,i) -> {
            v.setPadding(dp(22),i.getSystemWindowInsetTop()+dp(12),dp(22),i.getSystemWindowInsetBottom()+dp(12));
            return i;
        });
        setContentView(root);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹ Journal");
        back.setOnClickListener(v -> finish());
        heading.addView(back);
        TextView title = text("Discover",28,INK);
        title.setTypeface(null,Typeface.BOLD);
        heading.addView(title);
        root.addView(heading);
        areaLabel = text("",14,GREEN);
        areaLabel.setPadding(0,dp(12),0,dp(8));
        root.addView(areaLabel);
        LinearLayout controls = new LinearLayout(this);
        Button area = button("Choose area");
        Button settings = button("Setup & updates");
        controls.addView(area,new LinearLayout.LayoutParams(0,dp(52),1));
        controls.addView(settings,new LinearLayout.LayoutParams(0,dp(52),1));
        area.setOnClickListener(v -> chooseArea());
        settings.setOnClickListener(v -> setup());
        root.addView(controls);
        status = text("",14,INK);
        status.setPadding(0,dp(8),0,dp(12));
        root.addView(status);
        // Attribution stays visible in the same container as Google's result cards.
        LinearLayout attribution = new LinearLayout(this);
        TextView source = text("Google Maps",14,Color.rgb(94,94,94));
        source.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        source.setSingleLine(true);
        source.setPadding(0,dp(10),0,dp(8));
        attribution.addView(source,new LinearLayout.LayoutParams(0,-2,1));
        TextView limit = text("Up to 20 nearby restaurants",12,Color.rgb(94,94,94));
        limit.setPadding(dp(8),dp(10),0,dp(8));
        attribution.addView(limit);
        root.addView(attribution);
        ScrollView scroll = new ScrollView(this);
        results = column();
        scroll.addView(results);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        update = button("Update now");
        update.setTextColor(Color.WHITE);
        update.setBackground(shape(GREEN));
        root.addView(update,new LinearLayout.LayoutParams(-1,dp(56)));
        update.setOnClickListener(v -> load());
        root.addView(text("Your personal grades and comments stay in your journal.",12,GREEN));
        showStatus();
        showEmpty("Choose an area and complete Google setup to discover restaurants. Your journal is ready to use offline.");
        String revision=DiscoverySettings.prefs(this).getString("revision","");
        if (sessionPlaces!=null && revision.equals(sessionRevision)) render(sessionPlaces,sessionNewIds);
        else initialLoadPending = saved==null && DiscoverySettings.configured(this);
        if (saved!=null && saved.getBoolean("setup_open")) setup();
    }

    @Override protected void onStart() {
        super.onStart();
        if (initialLoadPending) { initialLoadPending=false; load(); }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("setup_open",setupDialog!=null && setupDialog.isShowing());
        // Deliberately omit API keys and provider response content.
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value,int size,int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextColor(GREEN); return b; }
    private GradientDrawable shape(int color) { GradientDrawable s = new GradientDrawable(); s.setColor(color); s.setCornerRadius(dp(18)); return s; }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    private boolean alive() { return !isFinishing() && !isDestroyed(); }

    private void showStatus() {
        SharedPreferences p = DiscoverySettings.prefs(this);
        boolean area = DiscoverySettings.hasArea(this);
        areaLabel.setText(area ? String.format(Locale.getDefault(),"Saved area: %.3f, %.3f · %d km radius",
                DiscoverySettings.latitude(this),DiscoverySettings.longitude(this),p.getInt("radius_km",5))
                : "Choose the area you want to explore");
        if (DiscoverySettings.hasUnreadableKey(this)) status.setText("The saved API key cannot be decrypted on this device. Enter it again in Setup & updates.");
        else if (DiscoverySettings.apiKey(this).isEmpty()) status.setText("Google setup needed. Add your restricted Places API key in Setup & updates.");
        else if (!DiscoverySettings.configured(this)) status.setText("Complete setup and choose an area to start discovery.");
        else {
            String daily = p.getBoolean("daily",true) ? "Daily updates enabled for this saved area." : "Daily updates off.";
            long last = p.getLong("last_success",0);
            String stamp = last == 0 ? "No successful update yet." : "Last refresh: " + DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(last));
            String error = p.getString("last_error","");
            status.setText(daily + "\n" + stamp + (error.isEmpty() ? "" : "\n" + error));
        }
        update.setEnabled(DiscoverySettings.configured(this));
    }

    private void showEmpty(String message) {
        results.removeAllViews();
        TextView empty = text(message,17,INK);
        empty.setPadding(dp(8),dp(30),dp(8),dp(30));
        results.addView(empty);
    }

    private void load() {
        if (!DiscoverySettings.configured(this)) { setup(); return; }
        if (request != null) request.cancel();
        int sequence = ++requestSequence;
        String revision = DiscoverySettings.prefs(this).getString("revision","");
        request = new CancellationTokenSource();
        update.setEnabled(false);
        status.setText("Loading nearby restaurants from Google Maps…");
        try {
            DiscoveryClient.search(this,false,request.getToken())
                    .addOnSuccessListener(response -> {
                        if (!alive() || sequence != requestSequence || !revision.equals(DiscoverySettings.prefs(this).getString("revision",""))) return;
                        Set<String> newIds = DiscoveryClient.record(this,response.getPlaces(),revision);
                        sessionPlaces=new ArrayList<>(response.getPlaces());
                        sessionNewIds=new HashSet<>(newIds); sessionRevision=revision;
                        render(sessionPlaces,sessionNewIds);
                        DiscoverySettings.prefs(this).edit().remove("new_ids").apply();
                        showStatus();
                    })
                    .addOnFailureListener(error -> {
                        if (!alive() || sequence != requestSequence || !revision.equals(DiscoverySettings.prefs(this).getString("revision",""))) return;
                        status.setText(DiscoveryClient.error(error));
                        showEmpty("Live discovery needs an internet connection and a working Google Places setup. Your journal is still available offline.");
                        update.setEnabled(true);
                    });
        } catch (Exception error) {
            status.setText(DiscoveryClient.error(error));
            update.setEnabled(true);
        }
    }

    private void render(List<Place> places,Set<String> newIds) {
        results.removeAllViews();
        if (places.isEmpty()) { showEmpty("Google returned no restaurants in this area. Try a wider radius or another location."); return; }
        for (Place place : places) {
            LinearLayout card = column();
            card.setPadding(dp(16),dp(14),dp(16),dp(14));
            card.setBackground(shape(Color.WHITE));
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1,-2);
            layout.setMargins(0,dp(6),0,dp(6));
            results.addView(card,layout);
            String name = place.getDisplayName() == null ? "Unnamed restaurant" : place.getDisplayName();
            TextView title = text(name,21,INK);
            title.setTypeface(null,Typeface.BOLD);
            card.addView(title);
            if (newIds.contains(place.getId())) card.addView(text("New to these results",12,GREEN));
            if (place.getFormattedAddress() != null) card.addView(text(place.getFormattedAddress(),14,Color.rgb(94,101,92)));
            if (place.getLocation() != null) {
                float[] distance = new float[1];
                Location.distanceBetween(DiscoverySettings.latitude(this),DiscoverySettings.longitude(this),
                        place.getLocation().latitude,place.getLocation().longitude,distance);
                card.addView(text(String.format(Locale.getDefault(),"%.1f km from your saved area",distance[0]/1000),13,GREEN));
            }
            if (place.getBusinessStatus() == Place.BusinessStatus.CLOSED_PERMANENTLY) card.addView(text("Listed as permanently closed",13,INK));
            if (place.getBusinessStatus() == Place.BusinessStatus.CLOSED_TEMPORARILY) card.addView(text("Listed as temporarily closed",13,INK));
            List<String> credits = place.getAttributions();
            if (credits != null) for (String credit : credits) {
                TextView attribution = text("",12,Color.rgb(94,94,94));
                attribution.setText(Html.fromHtml(credit,Html.FROM_HTML_MODE_LEGACY));
                attribution.setMovementMethod(LinkMovementMethod.getInstance());
                card.addView(attribution);
            }
            LinearLayout actions = new LinearLayout(this);
            Button map = button("Google Maps");
            map.setOnClickListener(v -> openUrl("https://www.google.com/maps/search/?api=1&query="
                    + Uri.encode(name) + "&query_place_id=" + Uri.encode(place.getId()==null?"":place.getId())));
            actions.addView(map,new LinearLayout.LayoutParams(0,dp(52),1));
            Button review = button("Add my review");
            review.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.putExtra("google_place_id",place.getId());
                setResult(RESULT_OK,intent);
                finish();
            });
            actions.addView(review,new LinearLayout.LayoutParams(0,dp(52),1));
            card.addView(actions);
        }
    }

    private void chooseArea() {
        track(new AlertDialog.Builder(this).setTitle("Where should we look?")
                .setMessage("Daily updates use this saved area. Changing it sends the new search area to Google. Your phone's location is only read when you choose it here.")
                .setPositiveButton("Use my location",(d,w) -> locate())
                .setNeutralButton("Enter coordinates",(d,w) -> coordinates())
                .setNegativeButton("Cancel",null).create()).show();
    }

    private void coordinates() {
        LinearLayout form = column(); form.setPadding(dp(24),dp(12),dp(24),0);
        EditText lat = input("Latitude, e.g. 32.08",false);
        EditText lon = input("Longitude, e.g. 34.78",false);
        if (DiscoverySettings.hasArea(this)) { lat.setText(Double.toString(DiscoverySettings.latitude(this))); lon.setText(Double.toString(DiscoverySettings.longitude(this))); }
        form.addView(lat); form.addView(lon);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Choose coordinates").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save area",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                double a = Double.parseDouble(lat.getText().toString().trim()), b = Double.parseDouble(lon.getText().toString().trim());
                if (!Double.isFinite(a) || !Double.isFinite(b) || a < -90 || a > 90 || b < -180 || b > 180) throw new NumberFormatException();
                cancelSearch();
                clearResults();
                DiscoverySettings.setArea(this,a,b);
                dialog.dismiss(); showStatus();
                if (DiscoverySettings.configured(this)) load();
            } catch (NumberFormatException error) { lat.setError("Use valid latitude (−90 to 90) and longitude (−180 to 180), with a decimal point."); }
        }));
        track(dialog).show();
    }

    private EditText input(String hint,boolean secret) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setTextSize(14);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        return e;
    }

    private void setup() {
        SharedPreferences p = DiscoverySettings.prefs(this);
        LinearLayout form = column(); form.setPadding(dp(24),dp(12),dp(24),dp(12));
        form.addView(text("Use your own Google Cloud project with billing and Places API (New) enabled. Restrict its API key to this Android app. Requests may be billed by Google.",14,INK));
        Button cloud = button("Google Cloud setup");
        cloud.setOnClickListener(v -> openUrl("https://developers.google.com/maps/documentation/places/android-sdk/cloud-setup"));
        form.addView(cloud);
        Button identity = button("Copy app identity for key restrictions");
        identity.setOnClickListener(v -> copyIdentity()); form.addView(identity);
        EditText key = input(DiscoverySettings.apiKey(this).isEmpty() ? "Places API key" : "Key saved · leave blank to keep it",true); form.addView(key);
        form.addView(text("Google requires public privacy and terms pages. Publish the supplied pages, then enter their HTTPS URLs below.",13,INK));
        EditText privacy = input("Your privacy page URL",false); privacy.setText(p.getString("privacy_url","")); form.addView(privacy);
        EditText terms = input("Your terms page URL",false); terms.setText(p.getString("terms_url","")); form.addView(terms);
        LinearLayout legal = new LinearLayout(this);
        Button viewPrivacy = button("Privacy"); viewPrivacy.setOnClickListener(v -> openUrl(privacy.getText().toString().trim())); legal.addView(viewPrivacy);
        Button viewTerms = button("Terms"); viewTerms.setOnClickListener(v -> openUrl(terms.getText().toString().trim())); legal.addView(viewTerms); form.addView(legal);
        form.addView(text("Search radius",15,GREEN));
        int[] radii = {1,3,5,10,20};
        Spinner radius = new Spinner(this);
        radius.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"1 km","3 km","5 km","10 km","20 km"}));
        int selected = 2; for (int i=0;i<radii.length;i++) if (radii[i]==p.getInt("radius_km",5)) selected=i;
        radius.setSelection(selected); form.addView(radius);
        Switch daily = new Switch(this); daily.setText("Refresh this area roughly daily"); daily.setChecked(p.getBoolean("daily",true)); daily.setMinHeight(dp(52)); form.addView(daily);
        form.addView(text("Daily refreshes need internet and may be delayed by Android. They discover place IDs; live names and addresses load when you open Discover. Your grades and comments are not uploaded.",13,INK));
        Button clear = button("Remove Google setup & stop updates"); form.addView(clear);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        setupDialog = new AlertDialog.Builder(this).setTitle("Google discovery setup").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        track(setupDialog);
        key.setSaveEnabled(false);
        setupDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        clear.setOnClickListener(v -> track(new AlertDialog.Builder(this).setTitle("Remove Google setup?").setMessage("This removes the API key and saved search area, and stops daily discovery. Your journal stays on this phone.")
                .setNegativeButton("Keep",null).setPositiveButton("Remove",(d,w) -> {cancelSearch(); stopLocation(); DiscoverySettings.clear(this); clearResults(); setupDialog.dismiss(); showStatus(); showEmpty("Google setup removed. Your journal is still available offline.");}).create()).show());
        setupDialog.setOnShowListener(d -> setupDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = key.getText().toString().trim(); if (value.isEmpty()) value = DiscoverySettings.apiKey(this);
            if (!value.startsWith("AIza") || value.length()<30 || value.length()>200) { key.setError("Enter your Google Places API key"); return; }
            String privacyUrl = privacy.getText().toString().trim(), termsUrl = terms.getText().toString().trim();
            if (!httpsUrl(privacyUrl)) { privacy.setError("Enter the HTTPS URL of your published privacy page"); return; }
            if (!httpsUrl(termsUrl)) { terms.setError("Enter the HTTPS URL of your published terms page"); return; }
            try {
                DiscoverySettings.save(this,value,radii[radius.getSelectedItemPosition()],daily.isChecked(),privacyUrl,termsUrl);
            } catch (DiscoverySettings.KeyStorageException error) {
                toast("Couldn't securely save the key on this device. Please try again."); return;
            } catch (java.io.IOException error) {
                toast("Couldn't save Google settings. Please try again."); return;
            } catch (Exception error) {
                toast("Couldn't securely save the key on this device. Please try again."); return;
            }
            cancelSearch(); clearResults(); DiscoveryClient.reset();
            setupDialog.dismiss(); showStatus();
            showEmpty("Setup saved. Choose an area and tap Update now.");
            DailyDiscoveryJob.schedule(this);
            if (DiscoverySettings.configured(this)) load();
        }));
        setupDialog.show();
    }

    private static boolean httpsUrl(String value) {
        Uri uri = Uri.parse(value);
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null && !uri.getHost().isEmpty() && uri.getUserInfo()==null;
    }

    private void openUrl(String url) {
        if (!httpsUrl(url)) { toast("Enter a valid HTTPS page URL first."); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException error) { toast("No app is available to open this link."); }
    }

    private void copyIdentity() {
        try {
            byte[] certificate;
            if (Build.VERSION.SDK_INT >= 28) certificate = getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.getApkContentsSigners()[0].toByteArray();
            else certificate = getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_SIGNATURES).signatures[0].toByteArray();
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(certificate);
            StringBuilder sha = new StringBuilder(); for (byte b : digest) { if (sha.length()>0) sha.append(':'); sha.append(String.format(Locale.ROOT,"%02X",b & 255)); }
            String identity = "Package: " + getPackageName() + "\nSHA-1: " + sha;
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Android app identity",identity));
            track(new AlertDialog.Builder(this).setTitle("Android key restriction").setMessage(identity).setPositiveButton("Copied",null).create()).show();
        } catch (Exception error) { toast("Couldn't read the app's signing identity."); }
    }

    private void locate() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION); return;
        }
        if (findingLocation) return;
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        try {
            if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) { toast("Turn on Location in your phone settings, or enter coordinates instead."); return; }
            Location last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null && android.os.SystemClock.elapsedRealtimeNanos() - last.getElapsedRealtimeNanos() < 600_000_000_000L) { acceptLocation(last); return; }
            findingLocation = true; status.setText("Finding your approximate location…");
            if (Build.VERSION.SDK_INT >= 30) {
                locationRequest = new CancellationSignal();
                locationManager.getCurrentLocation(LocationManager.NETWORK_PROVIDER,locationRequest,getMainExecutor(),location -> {
                    if (!findingLocation || !alive()) return;
                    stopLocation(); if (location != null) acceptLocation(location); else {showStatus();toast("Couldn't find your location. Try again or enter coordinates.");}
                });
            } else {
                legacyLocationListener = new LocationListener() {
                    public void onLocationChanged(Location location) { if (!findingLocation || !alive()) return; stopLocation(); acceptLocation(location); }
                    public void onStatusChanged(String provider,int status,Bundle extras) { }
                    public void onProviderEnabled(String provider) { }
                    public void onProviderDisabled(String provider) { stopLocation(); showStatus(); }
                };
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,legacyLocationListener,Looper.getMainLooper());
            }
            handler.postDelayed(() -> { if (findingLocation && alive()) {stopLocation();showStatus();toast("Location took too long. Try again or enter coordinates.");} },30000);
        } catch (SecurityException | IllegalArgumentException error) {stopLocation();showStatus();toast("Location is unavailable. You can enter coordinates instead.");}
    }

    private void acceptLocation(Location location) {
        cancelSearch(); clearResults(); DiscoverySettings.setArea(this,location.getLatitude(),location.getLongitude()); showStatus();
        showEmpty("Search area saved. Daily updates will use this area even if you travel elsewhere.");
        if (DiscoverySettings.configured(this)) load();
    }

    private void stopLocation() {
        findingLocation=false;
        handler.removeCallbacksAndMessages(null);
        if (locationRequest!=null) locationRequest.cancel();
        if (legacyLocationListener!=null && locationManager!=null) locationManager.removeUpdates(legacyLocationListener);
        locationRequest=null; legacyLocationListener=null;
    }

    private void cancelSearch() { requestSequence++; if (request!=null) request.cancel(); }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if (code==LOCATION_PERMISSION) {
            if (grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) locate();
            else toast("Location permission wasn't granted. You can choose coordinates instead.");
        }
    }
    @Override protected void onDestroy() {cancelSearch();stopLocation();handler.removeCallbacksAndMessages(null);if(setupDialog!=null)setupDialog.dismiss();for(AlertDialog dialog:dialogs)dialog.dismiss();super.onDestroy();}
}
