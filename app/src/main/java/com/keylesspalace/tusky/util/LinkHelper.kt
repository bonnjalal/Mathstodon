/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */
@file:JvmName("LinkHelper")

package com.keylesspalace.tusky.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.core.text.toHtml
import androidx.preference.PreferenceManager
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity.Companion.newHashtagIntent
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Status.Mention
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.view.MathJaxView

fun getDomain(urlString: String?): String {
    val host = urlString?.toUri()?.host
    return when {
        host == null -> ""
        host.startsWith("www.") -> host.substring(4)
        else -> host
    }
}

/**
 * Finds links, mentions, and hashtags in a piece of text and makes them clickable, associating
 * them with callbacks to notify when they're clicked.
 *
 * @param view the returned text will be put in
 * @param content containing text with mentions, links, or hashtags
 * @param mentions any '@' mentions which are known to be in the content
 * @param listener to notify about particular spans that are clicked
 */
fun setClickableText(view: TextView, content: CharSequence, mentions: List<Mention>, tags: List<HashTag>?, listener: LinkListener) {
    val spannableContent = markupHiddenUrls(view.context, content)

    view.text = spannableContent.apply {
        getSpans(0, content.length, URLSpan::class.java).forEach {
            setClickableText(it, this, mentions, tags, listener)
        }
    }
    view.movementMethod = LinkMovementMethod.getInstance()
}
fun setClickableText(view: MathJaxView, adapterPosition: Int, content: CharSequence, mentions: List<Mention>, tags: List<HashTag>?, listener: LinkListener): SpannableStringBuilder {
    val spannableContent = markupHiddenUrls(view.context, content)

//    val html = "<body onclick=\"onBodyClicked($adapterPosition)\">"
//    val htmlStart = "<a class=\"ext\" href=\"javascript:onBodyClicked($adapterPosition)\"></a></p>"
//    val htmlStart = "<p onclick=\"document.location.href='javascript:onBodyClicked($adapterPosition)';return true;\" class=\"ext\""
    val htmlStart = "<body onclick=\"location.href='https://www.bonnjalal.com/thread/$adapterPosition'\">"
    val htmlATag = "<a onclick=\"stopPropagation();\" "


    val spannable = spannableContent.apply {
        getSpans(0, content.length, URLSpan::class.java).forEach {
            setClickableTextHtml(it, this, mentions, tags, listener)
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val html = spannable.toHtml(Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE) //+ htmlEnd
        view.text = htmlStart + html
            .replace("<a", htmlATag)
//            .replace("<p", htmlStart)
    }else {
        val html = spannable.toHtml()// + htmlEnd
        view.text = htmlStart + html
            .replace("<a", htmlATag)
//            .replace("<p", htmlStart)
    }
//    html += "</p>"
//    view.text = html
    Log.e("Bonnjalal: ", "html : ${view.text}")
//    view.movementMethod = LinkMovementMethod.getInstance()

    return spannable
}

@VisibleForTesting
fun markupHiddenUrls(context: Context, content: CharSequence): SpannableStringBuilder {
    val spannableContent = SpannableStringBuilder.valueOf(content)
    val originalSpans = spannableContent.getSpans(0, content.length, URLSpan::class.java)
    val obscuredLinkSpans = originalSpans.filter {
        val text = spannableContent.subSequence(spannableContent.getSpanStart(it), spannableContent.getSpanEnd(it))
        val firstCharacter = text[0]
        return@filter if (firstCharacter == '#' || firstCharacter == '@') {
            false
        } else {
            var textDomain = getDomain(text.toString())
            if (textDomain.isBlank()) {
                // Allow "some.domain" or "www.some.domain" without a domain notifier
                textDomain = if (text.startsWith("www.")) {
                    text.substring(4)
                } else {
                    text.toString()
                }
            }
            getDomain(it.url) != textDomain
        }
    }

    for (span in obscuredLinkSpans) {
        val start = spannableContent.getSpanStart(span)
        val end = spannableContent.getSpanEnd(span)
        val originalText = spannableContent.subSequence(start, end)
        val replacementText = context.getString(R.string.url_domain_notifier, originalText, getDomain(span.url))
        spannableContent.replace(start, end, replacementText) // this also updates the span locations
    }

    return spannableContent
}

fun urlType(path: String): String {
    return if (path.contains("https://www.bonnjalal.com/tag/")){
        "tag"
    }else if (path.contains("https://www.bonnjalal.com/account/")){
        "account"
    }else if (path.contains("https://www.bonnjalal.com/thread/")) {
        "thread"
    }else {
        "url"
    }
//    return path.matches("^/@[^/]+$".toRegex()) ||
//            path.matches("^/@[^/]+/\\d+$".toRegex()) ||
//            path.matches("^/users/\\w+$".toRegex()) ||
//            path.matches("^/notice/[a-zA-Z0-9]+$".toRegex()) ||
//            path.matches("^/objects/[-a-f0-9]+$".toRegex()) ||
//            path.matches("^/notes/[a-z0-9]+$".toRegex()) ||
//            path.matches("^/display/[-a-f0-9]+$".toRegex()) ||
//            path.matches("^/profile/\\w+$".toRegex()) ||
//            path.matches("^/p/\\w+/\\d+$".toRegex()) ||
//            path.matches("^/\\w+$".toRegex())
}


@VisibleForTesting
fun setClickableText(
    span: URLSpan,
    builder: SpannableStringBuilder,
    mentions: List<Mention>,
    tags: List<HashTag>?,
    listener: LinkListener
) = builder.apply {
    val start = getSpanStart(span)
    val end = getSpanEnd(span)
    val flags = getSpanFlags(span)
    val text = subSequence(start, end)

    val customSpan = when (text[0]) {
        '#' -> getCustomSpanForTag(text, tags, span, listener)
        '@' -> getCustomSpanForMention(mentions, span, listener)
        else -> null
    } ?: object : NoUnderlineURLSpan(span.url) {
        override fun onClick(view: View) = listener.onViewUrl(url)
    }

    removeSpan(span)
    setSpan(customSpan, start, end, flags)

    /* Add zero-width space after links in end of line to fix its too large hitbox.
     * See also : https://github.com/tuskyapp/Tusky/issues/846
     *            https://github.com/tuskyapp/Tusky/pull/916 */
    if (end >= length || subSequence(end, end + 1).toString() == "\n") {
        insert(end, "\u200B")
    }
}

fun setClickableTextHtml(
    span: URLSpan,
    builder: SpannableStringBuilder,
    mentions: List<Mention>,
    tags: List<HashTag>?,
    listener: LinkListener
) = builder.apply {
    val start = getSpanStart(span)
    val end = getSpanEnd(span)
    val flags = getSpanFlags(span)
    val text = subSequence(start, end)

    val customSpan = when (text[0]) {
        '#' -> getCustomSpanForTagHtml(text, tags, span, listener)
        '@' -> getCustomSpanForMentionHtml(mentions, span, listener)
        else -> null
    } ?: object : NoUnderlineURLSpan(span.url) {
        override fun onClick(view: View) = listener.onViewUrl(url)
    }

    removeSpan(span)
    setSpan(customSpan, start, end, flags)

    /* Add zero-width space after links in end of line to fix its too large hitbox.
     * See also : https://github.com/tuskyapp/Tusky/issues/846
     *            https://github.com/tuskyapp/Tusky/pull/916 */
    if (end >= length || subSequence(end, end + 1).toString() == "\n") {
        insert(end, "\u200B")
    }
}

/*: String {

    var html = ""
    builder.apply {
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        val flags = getSpanFlags(span)
        val text = subSequence(start, end)

        val customSpan = when (text[0]) {
            '#' -> {
                html += "<a href=\"javascript:openHashtag('$text');\">$text </a>"

            }
            '@' -> html += "<a href=\"javascript:openAccount('$text');\">$text </a>"
            else -> html += "$text "
        }

        if (end >= length || subSequence(end, end + 1).toString() == "\n") {
            html += "&#8203;"
        }
    }
    return html
}*/

@VisibleForTesting
fun getTagName(text: CharSequence, tags: List<HashTag>?): String? {
    val scrapedName = normalizeToASCII(text.subSequence(1, text.length)).toString()
    return when (tags) {
        null -> scrapedName
        else -> tags.firstOrNull { it.name.equals(scrapedName, true) }?.name
    }
}

private fun getCustomSpanForTag(text: CharSequence, tags: List<HashTag>?, span: URLSpan, listener: LinkListener): ClickableSpan? {
    return getTagName(text, tags)?.let {
        object : NoUnderlineURLSpan(span.url) {
            override fun onClick(view: View) = listener.onViewTag(it)
        }
    }
}

/**
 * The url https://www.bonnjalal.com/tag/ is not workin just to make webview know its url
 * and then extract the tag name from it
 */
private fun getCustomSpanForTagHtml(text: CharSequence, tags: List<HashTag>?, span: URLSpan, listener: LinkListener): ClickableSpan? {
    return getTagName(text, tags)?.let {
//        object : NoUnderlineURLSpan("javascript:openHashtag('${it}')") {
        object : NoUnderlineURLSpan("https://www.bonnjalal.com/tag/$it") {
            override fun onClick(view: View) = listener.onViewTag(it)
        }
    }
}
fun Context.viewHashtag(tag: String){
    val intent = newHashtagIntent(this, tag)
    startActivity(intent)
}

fun Context.viewAccount(id: String){
    val intent = AccountActivity.getIntent(this, id)
    startActivity(intent)
}
private fun getCustomSpanForMention(mentions: List<Mention>, span: URLSpan, listener: LinkListener): ClickableSpan? {
    // https://github.com/tuskyapp/Tusky/pull/2339
    return mentions.firstOrNull { it.url == span.url }?.let {
        getCustomSpanForMentionUrl(span.url, it.id, listener)
    }
}

//fun Context.viewAccount(accountId: String){
//    val intent = AccountActivity.getIntent(this, accountId)
//    startActivityWithSlideInAnimation(intent)
//}

private fun getCustomSpanForMentionHtml(mentions: List<Mention>, span: URLSpan, listener: LinkListener): ClickableSpan? {
    // https://github.com/tuskyapp/Tusky/pull/2339
    return mentions.firstOrNull { it.url == span.url }?.let {
        getCustomSpanForMentionUrlHtml(span.url, it.id, listener)
    }
}

private fun getCustomSpanForMentionUrl(url: String, mentionId: String, listener: LinkListener): ClickableSpan {
    return object : NoUnderlineURLSpan(url) {
        override fun onClick(view: View) = listener.onViewAccount(mentionId)
    }
}
/**
 * The url https://www.bonnjalal.com/account/ is not workin just to make webview know its url
 * and then extract the tag name from it
 */
private fun getCustomSpanForMentionUrlHtml(url: String, mentionId: String, listener: LinkListener): ClickableSpan {
//    return object : NoUnderlineURLSpan("javascript:openAccount('$mentionId')") {
    return object : NoUnderlineURLSpan("https://www.bonnjalal.com/account/$mentionId") {
        override fun onClick(view: View) = listener.onViewAccount(mentionId)
    }
}

/**
 * Put mentions in a piece of text and makes them clickable, associating them with callbacks to
 * notify when they're clicked.
 *
 * @param view the returned text will be put in
 * @param mentions any '@' mentions which are known to be in the content
 * @param listener to notify about particular spans that are clicked
 */
fun setClickableMentions(view: TextView, mentions: List<Mention>?, listener: LinkListener) {
    if (mentions?.isEmpty() != false) {
        view.text = null
        return
    }

    view.text = SpannableStringBuilder().apply {
        var start = 0
        var end = 0
        var flags: Int
        var firstMention = true

        for (mention in mentions) {
            val customSpan = getCustomSpanForMentionUrl(mention.url, mention.id, listener)
            end += 1 + mention.localUsername.length // length of @ + username
            flags = getSpanFlags(customSpan)
            if (firstMention) {
                firstMention = false
            } else {
                append(" ")
                start += 1
                end += 1
            }

            append("@")
            append(mention.localUsername)
            setSpan(customSpan, start, end, flags)
            append("\u200B") // same reasoning as in setClickableText
            end += 1 // shift position to take the previous character into account
            start = end
        }
    }
    view.movementMethod = LinkMovementMethod.getInstance()
}

fun setClickableMentions(view: MathJaxView, mentions: List<Mention>?, listener: LinkListener) {
    if (mentions?.isEmpty() != false) {
        view.text = null
        return
    }

//    var html = ""
    view.text = SpannableStringBuilder().apply {
        var start = 0
        var end = 0
        var flags: Int
        var firstMention = true

        for (mention in mentions) {
            val customSpan = getCustomSpanForMentionUrlHtml(mention.url, mention.id, listener)
            end += 1 + mention.localUsername.length // length of @ + username
            flags = getSpanFlags(customSpan)
            if (firstMention) {
                firstMention = false
            } else {
                append(" ")
                start += 1
                end += 1
            }
//            html += "<a href=\"javascript:openAccount('${mention.localUsername}');\">@${mention.localUsername}<a/>&#8203;"
            append("@")
            append(mention.localUsername)
            setSpan(customSpan, start, end, flags)
            append("\u200B") // same reasoning as in setClickableText
            end += 1 // shift position to take the previous character into account
            start = end
        }
    }.toHtml()

//    Log.e("Bonnjalal : ", "Spannable: $spannable")
//    view.text = spannable.toHtml()


    /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        view.text = html//Html.toHtml(spannable, TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)

        Log.e("Bonnjalal : ", "html code ${view.text}")
    }else {
        view.text = html//Html.toHtml(spannable)
    }*/
//    view.movementMethod = LinkMovementMethod.getInstance()
}

fun createClickableText(text: String, link: String): CharSequence {
    return SpannableStringBuilder(text).apply {
        setSpan(NoUnderlineURLSpan(link), 0, text.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    }
}

/**
 * Opens a link, depending on the settings, either in the browser or in a custom tab
 *
 * @receiver the Context to open the link from
 * @param url a string containing the url to open
 */
fun Context.openLink(url: String) {
    val uri = url.toUri().normalizeScheme()
    val useCustomTabs = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("customTabs", false)

    if (useCustomTabs) {
        openLinkInCustomTab(uri, this)
    } else {
        openLinkInBrowser(uri, this)
    }
}

/**
 * opens a link in the browser via Intent.ACTION_VIEW
 *
 * @param uri the uri to open
 * @param context context
 */
private fun openLinkInBrowser(uri: Uri?, context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Activity was not found for intent, $intent")
    }
}

/**
 * tries to open a link in a custom tab
 * falls back to browser if not possible
 *
 * @param uri the uri to open
 * @param context context
 */
private fun openLinkInCustomTab(uri: Uri, context: Context) {
    val toolbarColor = ThemeUtils.getColor(context, R.attr.colorSurface)
    val navigationbarColor = ThemeUtils.getColor(context, android.R.attr.navigationBarColor)
    val navigationbarDividerColor = ThemeUtils.getColor(context, R.attr.dividerColor)
    val colorSchemeParams = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(toolbarColor)
        .setNavigationBarColor(navigationbarColor)
        .setNavigationBarDividerColor(navigationbarDividerColor)
        .build()
    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(colorSchemeParams)
        .setShowTitle(true)
        .build()

    try {
        customTabsIntent.launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Activity was not found for intent $customTabsIntent")
        openLinkInBrowser(uri, context)
    }
}

private const val TAG = "LinkHelper"
