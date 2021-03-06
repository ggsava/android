/*
 *  This file is part of eduVPN.
 *
 *     eduVPN is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     eduVPN is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with eduVPN.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.eduvpn.app.service;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import nl.eduvpn.app.entity.DiscoveredAPI;
import nl.eduvpn.app.entity.Instance;
import nl.eduvpn.app.entity.SavedProfile;
import nl.eduvpn.app.entity.SavedToken;
import nl.eduvpn.app.utils.Log;
import nl.eduvpn.app.utils.TTLCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Service which stores previously used access token and profile names.
 * This allows us to skip some steps, which will make the user experience more fluid.
 * Created by Daniel Zolnai on 2016-10-20.
 */
public class HistoryService {
    private static final String TAG = HistoryService.class.getName();

    private static final Long DISCOVERED_API_CACHE_TTL_SECONDS = 30 * 24 * 3600L; // 30 days

    private TTLCache<DiscoveredAPI> _discoveredAPICache;
    private List<SavedProfile> _savedProfileList;
    private List<SavedToken> _savedTokenList;

    private PreferencesService _preferencesService;

    /**
     * Constructor.
     *
     * @param preferencesService The preferences service which stores the app state.
     */
    public HistoryService(@NonNull PreferencesService preferencesService) {
        _preferencesService = preferencesService;
        _load();
        _discoveredAPICache.purge();
        // Save it immediately, because we just did a purge.
        // (It is better to purge at app start, since we do have some time now).
        _save();
    }

    /**
     * Loads the state of the service.
     */
    private void _load() {
        _savedProfileList = _preferencesService.getSavedProfileList();
        if (_savedProfileList == null) {
            _savedProfileList = new ArrayList<>();
            Log.i(TAG, "No saved profiles found.");
        }
        _savedTokenList = _preferencesService.getSavedTokenList();
        if (_savedTokenList == null) {
            _savedTokenList = new ArrayList<>();
            Log.i(TAG, "No saved tokens found.");
        }
        _discoveredAPICache = _preferencesService.getDiscoveredAPICache();
        if (_discoveredAPICache == null) {
            Log.i(TAG, "No discovered API cache found.");
            _discoveredAPICache = new TTLCache<>(DISCOVERED_API_CACHE_TTL_SECONDS);
        }
    }

    /**
     * Saves the state of the service.
     */
    private void _save() {
        _preferencesService.storeDiscoveredAPICache(_discoveredAPICache);
        _preferencesService.storeSavedProfileList(_savedProfileList);
        _preferencesService.storeSavedTokenList(_savedTokenList);
    }

    /**
     * Returns a discovered API from the cache.
     *
     * @param sanitizedBaseURI The sanitized base URI of the API.
     * @return A discovered API if a cached one was found. Null if none found.
     */
    @Nullable
    public DiscoveredAPI getCachedDiscoveredAPI(@NonNull String sanitizedBaseURI) {
        return _discoveredAPICache.get(sanitizedBaseURI);
    }

    /**
     * Caches a discovered API for future usage.
     *
     * @param sanitizedBaseURI The sanitized base URI of the API which was discovered.
     * @param discoveredAPI    The discovered API object to save.
     */
    public void cacheDiscoveredAPI(@NonNull String sanitizedBaseURI, @NonNull DiscoveredAPI discoveredAPI) {
        _discoveredAPICache.put(sanitizedBaseURI, discoveredAPI);
        _save();
    }

    /**
     * Returns a cached access token for an API.
     *
     * @param sanitizedBaseURI The sanitized base URI of the API.
     * @return The access token if found. Null if not found.
     */
    @Nullable
    public String getCachedAccessToken(@NonNull String sanitizedBaseURI) {
        for (SavedToken savedToken : _savedTokenList) {
            if (savedToken.getInstance().getSanitizedBaseURI().equals(sanitizedBaseURI)) {
                return savedToken.getAccessToken();
            }
        }
        return null;
    }

    /**
     * Caches an access token for an API.
     *
     * @param instance    The VPN provider the token is stored for.
     * @param accessToken The access token to save.
     */
    public void cacheAccessToken(@NonNull Instance instance, @NonNull String accessToken) {
        // Remove all previous entries
        removeAccessTokens(instance.getSanitizedBaseURI());
        _savedTokenList.add(new SavedToken(instance, accessToken));
        _save();
    }

    /**
     * Returns the unmodifiable list of saved profiles.
     *
     * @return The list of saved profiles. If none found, the list will be empty.
     */
    @NonNull
    public List<SavedProfile> getSavedProfileList() {
        return Collections.unmodifiableList(_savedProfileList);
    }

    /**
     * Stores a saved profile, so the user can select it the next time.
     *
     * @param savedProfile The saved profile to store.
     */
    public void cacheSavedProfile(@NonNull SavedProfile savedProfile) {
        _savedProfileList.add(savedProfile);
        _save();
    }

    /**
     * Returns a cached saved profile by looking it up by the API sanitized base URI and the profile ID.
     *
     * @param sanitizedBaseURI The sanitized base URI of the provider.
     * @param profileId        The unique ID of the profile within the provider.
     * @return The saved profile if found. Null if not found.
     */
    @Nullable
    public SavedProfile getCachedSavedProfile(@NonNull String sanitizedBaseURI, @NonNull String profileId) {
        for (SavedProfile savedProfile : _savedProfileList) {
            if (savedProfile.getInstance().getSanitizedBaseURI().equals(sanitizedBaseURI) &&
                    savedProfile.getProfile().getProfileId().equals(profileId)) {
                return savedProfile;
            }
        }
        return null;
    }

    /**
     * Saves a previously removed profile from the list.
     *
     * @param savedProfile The profile to remove.
     */
    public void removeSavedProfile(@NonNull SavedProfile savedProfile) {
        _savedProfileList.remove(savedProfile);
        _save();
    }

    /**
     * Removes the access token(s) which have the given base URI.
     *
     * @param sanitizedBaseURI The sanitized base URI of the provider.
     */
    public void removeAccessTokens(@NonNull String sanitizedBaseURI) {
        Iterator<SavedToken> savedTokenIterator = _savedTokenList.iterator();
        while (savedTokenIterator.hasNext()) {
            SavedToken savedToken = savedTokenIterator.next();
            if (savedToken.getInstance().getSanitizedBaseURI().equals(sanitizedBaseURI)) {
                savedTokenIterator.remove();
            }
        }
        _save();
    }

    /**
     * Removes a discovered API based on the provider.
     *
     * @param sanitizedBaseURI The sanitized base URI of the provider.
     */
    public void removeDiscoveredAPI(@NonNull String sanitizedBaseURI) {
        _discoveredAPICache.remove(sanitizedBaseURI);
        _save();
    }

    /**
     * Returns the list of all saved tokens.
     *
     * @return The list of all saved access tokens and instances.
     */
    public List<SavedToken> getSavedTokenList() {
        return Collections.unmodifiableList(_savedTokenList);
    }

    /**
     * Removes the saved profiles for an instance.
     *
     * @param sanitizedBaseURI The sanitized base URI of an instance.
     */
    public void removeSavedProfilesForInstance(@NonNull String sanitizedBaseURI) {
        Iterator<SavedProfile> savedProfileIterator = _savedProfileList.iterator();
        while (savedProfileIterator.hasNext()) {
            SavedProfile savedProfile = savedProfileIterator.next();
            if (savedProfile.getInstance().getSanitizedBaseURI().equals(sanitizedBaseURI)) {
                savedProfileIterator.remove();
            }
        }
        _save();
    }

    /**
     * Returns a saved token for a given sanitized base URI.
     * @param sanitizedBaseURI The base URI of the provider.
     * @return The token if available, otherwise null.
     */
    @Nullable
    public SavedToken getSavedToken(@NonNull String sanitizedBaseURI) {
        for (SavedToken savedToken : _savedTokenList) {
            if (sanitizedBaseURI.equals(savedToken.getInstance().getSanitizedBaseURI())) {
                return savedToken;
            }
        }
        return null;
    }
}
