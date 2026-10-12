package com.nikita.restaurantjournal;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
import com.google.android.libraries.places.api.net.SearchNearbyResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class DiscoveryClient {
    private static String initializedKey = "";
    private static PlacesClient client;

    static synchronized void reset() {
        if (Places.isInitialized()) Places.deinitialize();
        initializedKey = "";
        client = null;
    }

    private static synchronized PlacesClient client(Context context) {
        String key = DiscoverySettings.apiKey(context);
        if (key.isEmpty()) {
            if (DiscoverySettings.hasUnreadableKey(context)) throw new KeyUnavailableException();
            throw new IllegalStateException("Set up Google Places first.");
        }
        if (client == null || !key.equals(initializedKey)) {
            if (Places.isInitialized()) Places.deinitialize();
            Places.initializeWithNewPlacesApiEnabled(context.getApplicationContext(), key);
            client = Places.createClient(context.getApplicationContext());
            initializedKey = key;
        }
        return client;
    }

    static Task<SearchNearbyResponse> search(Context context, boolean idsOnly, CancellationToken token) {
        int radius = DiscoverySettings.prefs(context).getInt("radius_km", 5) * 1000;
        CircularBounds area = CircularBounds.newInstance(
                new LatLng(DiscoverySettings.latitude(context), DiscoverySettings.longitude(context)), radius);
        List<Place.Field> fields = idsOnly ? Collections.singletonList(Place.Field.ID)
                : Arrays.asList(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS,
                        Place.Field.LOCATION, Place.Field.BUSINESS_STATUS);
        SearchNearbyRequest request = SearchNearbyRequest.builder(area, fields)
                .setIncludedTypes(Collections.singletonList("restaurant"))
                .setMaxResultCount(20)
                .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
                .setCancellationToken(token).build();
        return client(context).searchNearby(request);
    }

    /** Google's place IDs are the only provider content retained on disk. */
    static synchronized Set<String> record(Context context, List<Place> places, String revision) {
        SharedPreferences p = DiscoverySettings.prefs(context);
        if (!revision.equals(p.getString("revision", ""))) return Collections.emptySet();
        Set<String> prior = new HashSet<>(p.getStringSet("result_ids", Collections.emptySet()));
        Set<String> pendingNew = new HashSet<>(p.getStringSet("new_ids", Collections.emptySet()));
        Set<String> current = new HashSet<>();
        for (Place place : places) if (place.getId() != null) current.add(place.getId());
        Set<String> added = new HashSet<>(current);
        added.removeAll(prior);
        if (!p.contains("last_success")) added.clear(); // First load establishes a baseline.
        added.addAll(pendingNew);
        added.retainAll(current);
        p.edit().putStringSet("result_ids", current).putStringSet("new_ids", added)
                .putLong("last_success", System.currentTimeMillis()).remove("last_error").apply();
        return added;
    }

    private static final class KeyUnavailableException extends IllegalStateException {}

    static String error(Exception error) {
        if (error instanceof KeyUnavailableException) return "The saved API key cannot be decrypted on this device. Enter it again in Setup & updates.";
        if (error instanceof ApiException) {
            int code = ((ApiException) error).getStatusCode();
            if (code == 7) return "No internet connection. Your journal still works offline.";
            if (code == 9010 || code == 9011 || code == 9012 || code == 9013 || code == 9014 || code == 9015)
                return "Google Places setup was rejected (code " + code + "). Check the API key, Android restrictions, enabled API and billing.";
            return "Google couldn't load nearby restaurants (code " + code + "). Check your connection, API setup and quota, then try again.";
        }
        return "Couldn't load nearby restaurants. Check your Google setup and connection, then try again.";
    }
}
