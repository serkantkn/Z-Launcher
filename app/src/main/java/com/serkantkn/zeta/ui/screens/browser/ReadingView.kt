package com.serkantkn.zeta.ui.screens.browser

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
      $ARTICLE_FINDER
      var found = __zuneFindArticle();
      if (!found) { return 'nothing'; }

      // Pictures a page loads only when scrolled to would stay blank once the page is replaced.
      var pictures = found.node.querySelectorAll('img');
      for (var i = 0; i < pictures.length; i++) {
        var late = pictures[i].getAttribute('data-src') || pictures[i].getAttribute('data-lazy-src');
        if (late) { pictures[i].setAttribute('src', new URL(late, location.href).href); pictures[i].removeAttribute('srcset'); }
        pictures[i].removeAttribute('loading');
      }
      // A story whose picture sits above the text, outside it, gets its share card's picture instead.
      var lead = '';
      if (!found.node.querySelector('img, picture, video')) {
        var card = document.querySelector('meta[property="og:image"], meta[name="twitter:image"]');
        var src = card && card.getAttribute('content');
        if (src) { lead = new URL(src, location.href).href; }
      }

      document.head.innerHTML =
        '<meta name="viewport" content="width=device-width, initial-scale=1">';
      document.body.innerHTML =
        '<div id="zune-reader"><h1 id="zune-reader-title"></h1><div id="zune-reader-body"></div></div>';
      document.getElementById('zune-reader-title').textContent = found.title;
      var body = document.getElementById('zune-reader-body');
      if (lead) {
        var picture = document.createElement('img');
        picture.src = lead;
        body.appendChild(picture);
      }
      while (found.node.firstChild) { body.appendChild(found.node.firstChild); }

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
 * Finds a page's story the way Firefox's reader does, as a script that defines
 * `__zuneFindArticle()`; it returns `{ title, node }` - a cleaned copy of the story - or null.
 *
 * Every paragraph of running text scores the boxes it sits in, the box with the most wins, and the
 * search then steps out past wrappers to the box holding clearly more of the story, so a story
 * split into one box per paragraph comes out whole. Siblings that read like the story join it. The
 * copy loses what is not the story: navigation, headers, share bars, lists of links, badges and
 * icons, and the page's own copy of the headline. Paragraphs in a floating banner (a cookie notice)
 * or in anything named like a sidebar, a comment thread or an advert never count. The page itself
 * is not touched.
 */
private val ARTICLE_FINDER = """
    function __zuneFindArticle() {
      var UNLIKELY = /-ad-|ad-break|agegate|banner|breadcrumb|combx|comment|community|disqus|extra|footer|gdpr|header|legends|menu|related|remark|replies|rss|shoutbox|sidebar|skyscraper|social|sponsor|supplemental|pagination|pager|popup|promo|teaser|consent|cookie|privacy|modal|dialog|overlay|newsletter|subscribe|share|tags|author-box|widget|weather|navbar|topbar|masthead/i;
      var LIKELY = /article|body|column|content|main|story|entry|post|text|detail/i;
      var BLOCK = 'p, pre, div, td, blockquote, section';
      var INNER_BLOCK = 'p, div, pre, table, ul, ol, blockquote, section, article, h1, h2, h3, h4, h5, h6, figure';
      var NEVER = 'nav, aside, footer, header, dialog, [role=dialog], [role=alertdialog], [aria-modal=true], [role=navigation], [role=banner], [role=contentinfo], form';
      function marks(el) { return (typeof el.className === 'string' ? el.className : '') + ' ' + (el.id || ''); }
      function unlikely(el) { var m = marks(el); return UNLIKELY.test(m) && !LIKELY.test(m); }
      function text(el) { return (el.textContent || '').replace(/\s+/g, ' ').trim(); }
      // Letters and digits only, so a heading in straight quotes still matches a title in curly ones.
      function plain(s) { return s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ''); }
      function linkDensity(el) {
        var length = text(el).length; if (!length) return 0;
        var linked = 0, links = el.querySelectorAll('a');
        for (var i = 0; i < links.length; i++) linked += text(links[i]).length;
        return linked / length;
      }
      function floating(el) {
        for (var up = el; up && up !== document.body; up = up.parentElement) {
          var position = getComputedStyle(up).position;
          if (position === 'fixed' || position === 'sticky') return true;
        }
        return false;
      }
      function title() {
        var meta = document.querySelector('meta[property="og:title"]');
        var full = ((meta && meta.getAttribute('content')) || document.title || '').trim();
        var headings = document.querySelectorAll('h1');
        for (var i = 0; i < headings.length; i++) {
          var h = text(headings[i]);
          if (h.length > 10 && plain(full).indexOf(plain(h)) >= 0) return h;
        }
        var parts = full.split(/\s[|\-–—»·:]\s/);
        if (parts.length > 1 && parts[0].split(/\s+/).length >= 3) return parts[0].trim();
        return full;
      }

      // Score each paragraph's ancestors by how much running text it carries, as Firefox's reader does.
      var scores = new Map(), running = new Map();
      function initial(el) {
        var s = 0;
        switch (el.tagName) {
          case 'ARTICLE': s += 10; break;
          case 'DIV': case 'SECTION': case 'MAIN': s += 5; break;
          case 'PRE': case 'TD': case 'BLOCKQUOTE': s += 3; break;
          case 'FORM': case 'OL': case 'UL': case 'DL': case 'ADDRESS': case 'LI': s -= 3; break;
          case 'H1': case 'H2': case 'H3': case 'H4': case 'H5': case 'H6': case 'TH': s -= 5; break;
        }
        var m = marks(el);
        if (LIKELY.test(m)) s += 25;
        if (UNLIKELY.test(m) && !LIKELY.test(m)) s -= 25;
        return s;
      }
      var blocks = document.body.querySelectorAll(BLOCK);
      for (var i = 0; i < blocks.length; i++) {
        var block = blocks[i];
        if (block.tagName !== 'P' && block.tagName !== 'PRE' && block.querySelector(INNER_BLOCK)) continue;
        var words = text(block);
        if (words.length < 25) continue;
        if (block.closest(NEVER) || block.closest('a, li')) continue;
        var noise = false;
        for (var up = block, depth = 0; up && depth < 4; up = up.parentElement, depth++) if (unlikely(up)) { noise = true; break; }
        if (noise || floating(block)) continue;
        var score = 1 + words.split(/[,،，]/).length - 1 + Math.min(Math.floor(words.length / 100), 3);
        for (var holder = block.parentElement; holder && holder !== document.documentElement; holder = holder.parentElement) {
          running.set(holder, (running.get(holder) || 0) + words.length);
        }
        var ancestor = block.parentElement;
        for (var level = 0; ancestor && ancestor !== document.documentElement && level < 5; level++, ancestor = ancestor.parentElement) {
          if (!scores.has(ancestor)) scores.set(ancestor, initial(ancestor));
          scores.set(ancestor, scores.get(ancestor) + score / (level === 0 ? 1 : level === 1 ? 2 : level * 3));
        }
      }
      var top = null, topScore = 0;
      scores.forEach(function (s, el) {
        var final = s * (1 - linkDensity(el));
        scores.set(el, final);
        if (final > topScore) { topScore = final; top = el; }
      });
      if (!top) return null;

      // A story split into one box per paragraph scores each box alone: step out to the box that holds
      // clearly more of the running text, past any wrappers, for as long as there is more to gain.
      for (var jump = 0; jump < 6; jump++) {
        var found = null;
        for (var up2 = top.parentElement, d2 = 0; up2 && up2 !== document.body && d2 < 6; up2 = up2.parentElement, d2++) {
          if ((running.get(up2) || 0) >= (running.get(top) || 0) * 1.4) { found = up2; break; }
        }
        if (!found || linkDensity(found) > 0.4 || (running.get(found) || 0) < text(found).length * 0.35) break;
        top = found;
        topScore = Math.max(topScore, scores.get(found) || 0);
      }

      // The siblings that belong to the story too: a lead paragraph, a second column of the text.
      var parts = [];
      var siblings = top.parentElement ? top.parentElement.children : [top];
      var threshold = Math.max(10, topScore * 0.2);
      for (var k = 0; k < siblings.length; k++) {
        var sibling = siblings[k];
        if (sibling === top) { parts.push(sibling); continue; }
        if (sibling.closest(NEVER) || unlikely(sibling)) continue;
        var sScore = scores.get(sibling) || 0;
        var sText = text(sibling), sLinks = linkDensity(sibling);
        if (sScore >= threshold ||
            (sibling.tagName === 'P' && sText.length > 80 && sLinks < 0.25) ||
            (sibling.tagName === 'P' && sText.length > 0 && sLinks === 0 && /\.( |$)/.test(sText))) parts.push(sibling);
      }

      // Copies, so the page itself is never touched; small pictures (badges, icons) are noted first.
      var container = document.createElement('div');
      for (var p = 0; p < parts.length; p++) {
        var original = parts[p];
        var images = original.querySelectorAll('img');
        var small = [];
        for (var q = 0; q < images.length; q++) {
          var img = images[q];
          var w = img.getBoundingClientRect().width || parseInt(img.getAttribute('width') || '0', 10);
          var h = img.getBoundingClientRect().height || parseInt(img.getAttribute('height') || '0', 10);
          var box = img.getBoundingClientRect();
          var lazy = img.hasAttribute('data-src') || img.hasAttribute('data-lazy-src') || img.loading === 'lazy';
          small.push((w > 0 && w < 140) || (h > 0 && h < 80) ||
            (box.width === 0 && box.height === 0 && !lazy) ||
            /(^|[\/_.-])(badge|logo|sprite|avatar|emoji|spacer|icon|google-g)s?([\/_.-]|$)/i.test(img.getAttribute('src') || ''));
        }
        var copy = original.cloneNode(true);
        var copies = copy.querySelectorAll('img');
        for (var r = 0; r < copies.length; r++) if (small[r]) copies[r].setAttribute('data-zune-small', '1');
        container.appendChild(copy);
      }
      var strip = container.querySelectorAll(
        'script, style, link, iframe, noscript, svg, button, input, select, textarea, object, embed, ' +
        NEVER + ', [aria-hidden=true], [hidden], img[data-zune-small]'
      );
      for (var s1 = 0; s1 < strip.length; s1++) strip[s1].remove();
      var all = container.querySelectorAll('*');
      for (var s2 = all.length - 1; s2 >= 0; s2--) {
        var el = all[s2];
        if (!container.contains(el)) continue;
        if (/^(DIV|SECTION|UL|OL|TABLE|FIGURE|SPAN|A)$/.test(el.tagName) && unlikely(el)) { el.remove(); continue; }
        if (!/^(DIV|SECTION|UL|OL|TABLE|FORM)$/.test(el.tagName)) continue;
        var content = text(el), density = linkDensity(el), pictures = el.querySelectorAll('img').length;
        var paragraphs = el.querySelectorAll('p').length, items = el.querySelectorAll('li').length;
        var commas = content.split(',').length - 1;
        if (commas >= 10) continue;
        if ((content.length < 25 && pictures === 0 && !el.querySelector('video, picture, h2, h3, h4, h5, h6, pre, blockquote')) ||
            (density > 0.5) ||
            (density > 0.2 && content.length < 200) ||
            (items > paragraphs && el.tagName !== 'UL' && el.tagName !== 'OL' && density > 0.2) ||
            (pictures > 2 && paragraphs / pictures < 0.5 && content.length < 200)) el.remove();
      }
      var heading = title();
      var heads = container.querySelectorAll('h1, h2');
      for (var t = 0; t < heads.length; t++) if (plain(text(heads[t])) === plain(heading)) heads[t].remove();
      if (text(container).length < 140) return null;
      return { title: heading, node: container };
    }
""".trimIndent()

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

/**
 * The page's story as reading view would keep it, without touching the page: its title, the
 * story's HTML with pictures and links made absolute, and its share card's picture and summary -
 * as a JSON string, or "nothing" when there is no story to keep.
 */
internal fun readingCopyScript(): String = """
    (function () {
      $ARTICLE_FINDER
      var best = null, heading = '';
      if (window.__zuneReader) {
        best = document.getElementById('zune-reader-body');
        heading = (document.getElementById('zune-reader-title') || {}).textContent || '';
      } else {
        var found = __zuneFindArticle();
        if (found) { best = found.node; heading = found.title; }
      }
      if (!best) { return 'nothing'; }
      var copy = best.cloneNode(true);
      var strip = copy.querySelectorAll(
        'script, style, iframe, nav, aside, header, footer, form, button, noscript, svg, ' +
        '[role=navigation], [aria-hidden=true]'
      );
      for (var j = 0; j < strip.length; j++) { strip[j].remove(); }
      var pictures = copy.querySelectorAll('img');
      for (var k = 0; k < pictures.length; k++) {
        var img = pictures[k];
        var src = img.getAttribute('data-src') || img.currentSrc || img.src;
        if (src) { img.setAttribute('src', new URL(src, location.href).href); }
        img.removeAttribute('srcset');
        img.removeAttribute('loading');
      }
      var links = copy.querySelectorAll('a[href]');
      for (var m = 0; m < links.length; m++) { links[m].setAttribute('href', links[m].href); }
      function meta(name) {
        var tag = document.querySelector('meta[property="' + name + '"], meta[name="' + name + '"]');
        return tag ? (tag.getAttribute('content') || '') : '';
      }
      return JSON.stringify({
        title: (heading || meta('og:title') || document.title || '').trim(),
        html: copy.innerHTML,
        image: meta('og:image'),
        excerpt: meta('og:description')
      });
    })();
""".trimIndent()

/** What [readingCopyScript] found, read out of the string evaluateJavascript hands back. */
internal class ReadingCopy(val title: String, val html: String, val image: String?, val excerpt: String) {
    companion object {
        fun parse(result: String?): ReadingCopy? {
            if (result.isNullOrBlank() || result == "null") return null
            return try {
                val text = org.json.JSONArray("[$result]").getString(0)
                if (text == "nothing") return null
                val obj = org.json.JSONObject(text)
                ReadingCopy(
                    title = obj.optString("title"),
                    html = obj.optString("html"),
                    image = obj.optString("image").takeIf { it.startsWith("http") },
                    excerpt = obj.optString("excerpt")
                ).takeIf { it.html.isNotBlank() }
            } catch (e: org.json.JSONException) {
                null
            }
        }
    }
}
