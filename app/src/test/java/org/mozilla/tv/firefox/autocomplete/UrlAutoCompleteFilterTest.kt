/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.autocomplete

import android.app.Application
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mozilla.tv.firefox.R
import org.mozilla.tv.firefox.components.UrlAutoCompleteFilter
import org.mozilla.tv.firefox.widget.InlineAutocompleteEditText
import org.mozilla.tv.firefox.widget.InlineAutocompleteEditText.AutocompleteResult
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(packageName = "org.mozilla.tv.firefox")
class UrlAutoCompleteFilterTest {
    private lateinit var appContext: Application

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(appContext)
                .edit()
                .clear()
                .apply()
    }

    @Test
    fun testAutocompletion() {
        var job = Job()
        val filter = UrlAutoCompleteFilter()
        filter.load(appContext, job, false)

        val domains = listOf("mozilla.org", "google.com", "facebook.com")
        filter.onDomainsLoaded(domains)

        assertAutocompletion(filter, "m", "mozilla.org")
        assertAutocompletion(filter, "www", "www.mozilla.org")
        assertAutocompletion(filter, "www.face", "www.facebook.com")
        assertAutocompletion(filter, "MOZ", "MOZilla.org")
        assertAutocompletion(filter, "www.GOO", "www.GOOgle.com")
        assertAutocompletion(filter, "WWW.GOOGLE.", "WWW.GOOGLE.com")
        assertAutocompletion(filter, "www.facebook.com", "www.facebook.com")
        assertAutocompletion(filter, "facebook.com", "facebook.com")

        assertNoAutocompletion(filter, "wwww")
        assertNoAutocompletion(filter, "yahoo")

        job.cancel()
    }

//    @Test
    fun testAutocompletionWithCustomDomains() {
        PreferenceManager.getDefaultSharedPreferences(appContext)
                .edit()
                .putBoolean(appContext.getString(R.string.pref_key_autocomplete_custom), true)
                .apply()

        val domains = listOf("facebook.com", "google.com", "mozilla.org")

        val job = Job()
        val filter = UrlAutoCompleteFilter()
        filter.load(appContext, job, false)
        filter.onDomainsLoaded(domains)

        assertAutocompletion(filter, "f", "fanfiction.com")
        assertAutocompletion(filter, "fa", "fanfiction.com")
        assertAutocompletion(filter, "fac", "facebook.com")

        assertAutocompletion(filter, "g", "gap.com")
        assertAutocompletion(filter, "go", "google.com")
        assertAutocompletion(filter, "ga", "gap.com")

        assertAutocompletion(filter, "m", "mobile.de")
        assertAutocompletion(filter, "mo", "mobile.de")
        assertAutocompletion(filter, "mob", "mobile.de")
        assertAutocompletion(filter, "moz", "mozilla.org")

        job.cancel()
    }

    @Test
    fun testWithoutDomains() {
        val filter = UrlAutoCompleteFilter()

        assertNoAutocompletion(filter, "mozilla")
    }

    @Test
    fun testWithoutView() {
        val filter = UrlAutoCompleteFilter()
        filter.onFilter("mozilla", null)
    }

    private fun assertAutocompletion(filter: UrlAutoCompleteFilter, text: String, completion: String) {
        val view = mock(InlineAutocompleteEditText::class.java)
        filter.onFilter(text, view)

        val captor = ArgumentCaptor.forClass(AutocompleteResult::class.java)
        verify(view).onAutocomplete(captor.capture())

        assertFalse(captor.value.isEmpty)
        assertEquals(completion, captor.value.text)
    }

    private fun assertNoAutocompletion(filter: UrlAutoCompleteFilter, text: String) {
        val view = mock(InlineAutocompleteEditText::class.java)
        filter.onFilter(text, view)

        val captor = ArgumentCaptor.forClass(AutocompleteResult::class.java)
        verify(view).onAutocomplete(captor.capture())

        assertTrue(captor.value.isEmpty)
    }
}
