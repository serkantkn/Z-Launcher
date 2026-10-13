package com.serkantkn.zunelauncher.ui.screens.browser

/**
 * Windows Phone's reading view, as much of it as a page will allow.
 *
 * There is no way to ask a website for "just the article", so this does what every reader does:
 * finds the block of the page carrying the most text, throws the rest away and sets the remainder
 * in something readable. It is a guess, and on a page that is not an article it will guess badly —
 * which is why leaving reading view simply loads the page again.
 */
internal fun readingViewScript(isDark: Boolean): String {
    val ink = if (isDark) "#e8e8e8" else "#1a1a1a"
    val paper = if (isDark) "#121212" else "#fdfdfb"
    val quiet = if (isDark) "#9a9a9a" else "#666666"
    val link = if (isDark) "#7fb2ff" else "#1a56b0"
    return """
    (function () {
      if (window.__zuneReader) { return 'already'; }
      var candidates = document.querySelectorAll(
        'article, main, [role=main], #content, #main, .content, .post, .entry, .article, body'
      );
      var best = null, bestScore = 0;
      for (var i = 0; i < candidates.length; i++) {
        var el = candidates[i];
        var text = el.innerText || '';
        var score = text.length;
        if (el.tagName === 'ARTICLE') score *= 1.6;
        if (el.tagName === 'BODY') score *= 0.5;
        if (score > bestScore) { bestScore = score; best = el; }
      }
      if (!best || bestScore < 200) { return 'nothing'; }

      var title = document.title || '';
      var body = best.innerHTML;
      best.querySelectorAll && null;

      document.head.innerHTML =
        '<meta name="viewport" content="width=device-width, initial-scale=1">';
      document.body.innerHTML =
        '<div id="zune-reader"><h1 id="zune-reader-title"></h1><div id="zune-reader-body"></div></div>';
      document.getElementById('zune-reader-title').textContent = title;
      document.getElementById('zune-reader-body').innerHTML = body;

      var strip = document.querySelectorAll(
        '#zune-reader script, #zune-reader iframe, #zune-reader nav, #zune-reader aside, ' +
        '#zune-reader header, #zune-reader footer, #zune-reader form, #zune-reader button, ' +
        '#zune-reader [role=navigation], #zune-reader [aria-hidden=true]'
      );
      for (var j = 0; j < strip.length; j++) { strip[j].remove(); }

      var style = document.createElement('style');
      style.textContent =
        'html,body{margin:0;padding:0;background:$paper !important;}' +
        '#zune-reader{max-width:38em;margin:0 auto;padding:28px 20px 64px;' +
        'font-family:-apple-system,Segoe UI,Roboto,sans-serif;font-size:18px;line-height:1.65;' +
        'color:$ink;background:$paper;}' +
        '#zune-reader h1{font-weight:300;font-size:30px;line-height:1.25;margin:0 0 18px;}' +
        '#zune-reader h2,#zune-reader h3{font-weight:400;line-height:1.3;margin:28px 0 10px;}' +
        '#zune-reader p{margin:0 0 18px;}' +
        '#zune-reader a{color:$link;}' +
        '#zune-reader img{max-width:100%;height:auto;display:block;margin:18px auto;}' +
        '#zune-reader figcaption,#zune-reader small{color:$quiet;font-size:15px;}' +
        '#zune-reader pre,#zune-reader code{font-size:15px;overflow-x:auto;}' +
        '#zune-reader table{display:block;overflow-x:auto;max-width:100%;}' +
        '#zune-reader *{max-width:100%;}';
      document.head.appendChild(style);
      window.__zuneReader = true;
      window.scrollTo(0, 0);
      return 'ok';
    })();
    """.trimIndent()
}

/**
 * Asks a loaded page, without touching it, whether reading view would make something of it, in
 * the way Firefox decides whether to offer its reader: "yes" when the page holds enough long
 * paragraphs of running text - the shape of an article - and "no" for a front page of headlines,
 * a search, a gallery; "reading" when it already is in reading view.
 *
 * What does not count is what a front page is made of: short teasers, the summaries inside list
 * items and inside links, paragraphs that are mostly links, and anything in a sidebar, a comment
 * thread, a footer or an advert - nor the cookie and privacy notices sites open over the page.
 */
internal fun readingAvailabilityScript(): String = """
    (function () {
      if (window.__zuneReader) { return 'reading'; }
      var unlikely = /-ad-|ad-break|agegate|banner|breadcrumb|combx|comment|community|disqus|extra|footer|gdpr|header|legends|menu|related|remark|replies|rss|shoutbox|sidebar|skyscraper|social|sponsor|supplemental|pagination|pager|popup|promo|teaser|card|consent|cookie|privacy|modal|dialog|overlay|notice|newsletter|subscribe/i;
      var likely = /article|body|column|content|main|story|text/i;
      var nodes = document.querySelectorAll('p, pre');
      var score = 0;
      for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        if (!node.offsetParent && node.offsetHeight === 0) { continue; }
        if (node.closest('li, a, nav, aside, footer, header, figcaption, dialog, [role=dialog], [role=alertdialog], [aria-modal=true]')) { continue; }
        var marks = '', up = node;
        for (var depth = 0; up && depth < 4; depth++, up = up.parentElement) {
          marks += ' ' + (up.className && up.className.baseVal === undefined ? up.className : '') + ' ' + (up.id || '');
        }
        if (unlikely.test(marks) && !likely.test(marks)) { continue; }
        var length = (node.textContent || '').trim().length;
        if (length < $MIN_PARAGRAPH) { continue; }
        var linked = 0, links = node.querySelectorAll('a');
        for (var j = 0; j < links.length; j++) { linked += (links[j].textContent || '').length; }
        if (linked / length > 0.3) { continue; }
        score += Math.sqrt(length - $MIN_PARAGRAPH);
        if (score > $READABLE_SCORE) { return 'yes'; }
      }
      return 'no';
    })();
""".trimIndent()

/** A paragraph shorter than this is a caption or a teaser, not running text. */
private const val MIN_PARAGRAPH = 140

/** How much running text, scored as Firefox scores it, makes a page worth offering reading view for. */
private const val READABLE_SCORE = 20
