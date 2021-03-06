/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.components

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.mozilla.tv.firefox.components.locale.Locales
import org.mozilla.tv.firefox.utils.Settings
import org.mozilla.tv.firefox.widget.InlineAutocompleteEditText
import org.mozilla.tv.firefox.widget.InlineAutocompleteEditText.AutocompleteResult
import java.io.IOException
import java.util.Locale

class UrlAutoCompleteFilter : InlineAutocompleteEditText.OnFilterListener {
    companion object {
        private val LOG_TAG = "UrlAutoCompleteFilter"
    }

    object AutocompleteSource {
        const val DEFAULT_LIST = "default"
    }

    private var settings: Settings? = null

    private var preInstalledDomains: List<String> = emptyList()

    override fun onFilter(rawSearchText: String, view: InlineAutocompleteEditText?) {
        if (view == null) {
            return
        }

        // Search terms are all lowercase already, we just need to lowercase the search text
        val searchText = rawSearchText.toLowerCase(Locale.US)

        settings?.let {
            if (it.shouldAutocompleteFromShippedDomainList()) {
                val autocomplete = tryToAutocomplete(searchText, preInstalledDomains)
                if (autocomplete != null) {
                    view.onAutocomplete(prepareAutocompleteResult(
                            rawSearchText,
                            autocomplete,
                            AutocompleteSource.DEFAULT_LIST,
                            preInstalledDomains.size))
                    return
                }
            }
        }

        view.onAutocomplete(AutocompleteResult.emptyResult())
    }

    private fun tryToAutocomplete(searchText: String, domains: List<String>): String? {
        domains.forEach {
            val wwwDomain = "www." + it
            if (wwwDomain.startsWith(searchText)) {
                return wwwDomain
            }

            if (it.startsWith(searchText)) {
                return it
            }
        }

        return null
    }

    internal fun onDomainsLoaded(domains: List<String>) {
        this.preInstalledDomains = domains
    }

    fun load(context: Context, uiLifecycleCancelJob: Job, loadDomainsFromDisk: Boolean = true) {
        val uiScope = CoroutineScope(Dispatchers.Main + uiLifecycleCancelJob)
        settings = Settings.getInstance(context)
        if (loadDomainsFromDisk) {
            uiScope.launch {
                val domains = async { loadDomains(context) }

                onDomainsLoaded(domains.await())
            }
        }
    }

    private suspend fun loadDomains(context: Context): List<String> {
        val domains = LinkedHashSet<String>()
        val availableLists = getAvailableDomainLists(context)

        // First load the country specific lists following the default locale order
        Locales.getCountriesInDefaultLocaleList()
                .asSequence()
                .filter { availableLists.contains(it) }
                .forEach { loadDomainsForLanguage(context, domains, it) }

        // And then add domains from the global list
        loadDomainsForLanguage(context, domains, "global")

        return domains.toList()
    }

    private fun getAvailableDomainLists(context: Context): Set<String> {
        val availableDomains = LinkedHashSet<String>()

        val assetManager = context.assets

        try {
            availableDomains.addAll(assetManager.list("domains")!!)
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Could not list domain list directory")
        }

        return availableDomains
    }

    private fun loadDomainsForLanguage(context: Context, domains: MutableSet<String>, country: String) {
        val assetManager = context.assets

        try {
            domains.addAll(
                    assetManager.open("domains/" + country).bufferedReader().readLines())
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Could not load domain list: " + country)
        }
    }

    /**
     * Our autocomplete list is all lower case, however the search text might be mixed case.
     * Our autocomplete EditText code does more string comparison, which fails if the suggestion
     * doesn't exactly match searchText (ie. if casing differs). It's simplest to just build a suggestion
     * that exactly matches the search text - which is what this method is for:
     */
    private fun prepareAutocompleteResult(
        rawSearchText: String,
        lowerCaseResult: String,
        source: String,
        totalCount: Int
    ) =
            AutocompleteResult(
                    rawSearchText + lowerCaseResult.substring(rawSearchText.length),
                    source,
                    totalCount)
}
