package com.minireader.app.ui.reader

import com.minireader.app.data.prefs.FontChoice
import com.minireader.app.data.prefs.ReaderPrefs
import com.minireader.app.data.prefs.ReaderTheme
import com.minireader.app.data.prefs.TextAlignment

object ReaderStyle {

    fun css(prefs: ReaderPrefs, colors: ReaderColors): String {
        val fontStack = when (prefs.fontFamily) {
            FontChoice.SERIF -> "\"Noto Serif CJK TC\", \"Noto Serif TC\", \"Songti TC\", \"Source Han Serif TC\", serif"
            FontChoice.SANS -> "\"Noto Sans CJK TC\", \"Noto Sans TC\", \"PingFang TC\", \"Source Han Sans TC\", sans-serif"
            FontChoice.BOLD -> "\"Noto Sans CJK TC\", \"Noto Sans TC\", \"PingFang TC\", \"Source Han Sans TC\", sans-serif"
        }
        val boldRule = if (prefs.fontFamily == FontChoice.BOLD) {
            "body, body * { font-weight: 700 !important; }"
        } else {
            ""
        }
        val grayscaleRule = if (prefs.theme == ReaderTheme.GRAY) {
            "img, svg, video, canvas { filter: grayscale(100%) !important; }"
        } else {
            ""
        }
        val alignment = when (prefs.textAlignment) {
            TextAlignment.LEFT -> "left"
            TextAlignment.RIGHT -> "right"
            TextAlignment.CENTER -> "center"
            TextAlignment.JUSTIFY -> "justify"
        }
        val indent = if (
            prefs.firstLineIndent &&
            (prefs.textAlignment == TextAlignment.LEFT ||
                prefs.textAlignment == TextAlignment.JUSTIFY)
        ) {
            "2em"
        } else {
            "0em"
        }
        val justifyRule = if (prefs.textAlignment == TextAlignment.JUSTIFY) {
            "text-justify: inter-ideograph;"
        } else {
            "text-justify: none;"
        }
        return """
            :root {
              --mr-bg: ${colors.background.toCssHex()};
              --mr-fg: ${colors.foreground.toCssHex()};
              --mr-link: ${colors.link.toCssHex()};
              --mr-fs: ${prefs.fontSizeSp}px;
              --mr-lh: ${prefs.lineHeight};
              --mr-ls: ${prefs.letterSpacing}em;
              --mr-para: ${prefs.paragraphSpacing}em;
              --mr-indent: $indent;
              --mr-margin-x: ${prefs.horizontalMarginDp}px;
            }
            html {
              -webkit-text-size-adjust: 100% !important;
              text-size-adjust: 100% !important;
            }
            html, body {
              background: var(--mr-bg) !important;
              color: var(--mr-fg) !important;
            }
            body {
              font-family: $fontStack !important;
              font-size: var(--mr-fs) !important;
              line-height: var(--mr-lh) !important;
              letter-spacing: var(--mr-ls) !important;
              text-align: $alignment !important;
              $justifyRule
              margin: 0 !important;
              padding: 8px var(--mr-margin-x) 12px var(--mr-margin-x) !important;
              word-wrap: break-word !important;
              overflow-wrap: break-word !important;
              -webkit-font-smoothing: antialiased;
            }
            body * {
              font-family: inherit !important;
            }
            code, pre, kbd, samp {
              font-family: monospace !important;
            }
            body, p, div, section, article, main, aside, li, blockquote, td, th {
              line-height: var(--mr-lh) !important;
              letter-spacing: var(--mr-ls) !important;
              background-color: transparent !important;
              color: var(--mr-fg) !important;
            }
            p {
              margin: 0 0 var(--mr-para) 0 !important;
              text-indent: var(--mr-indent) !important;
            }
            h1, h2, h3, h4, h5, h6 {
              line-height: 1.35 !important;
              text-indent: 0 !important;
              color: var(--mr-fg) !important;
              background-color: transparent !important;
            }
            a {
              color: var(--mr-link) !important;
              text-decoration: none !important;
            }
            img, svg, video {
              max-width: 100% !important;
              height: auto !important;
            }
            table {
              max-width: 100% !important;
              border-collapse: collapse;
            }
            pre, code {
              white-space: pre-wrap !important;
              word-break: break-word !important;
            }
            hr {
              border-color: var(--mr-fg);
              opacity: 0.2;
            }
            .mr-chapter-nav {
              display: flex;
              justify-content: center;
              align-items: center;
              margin: 28px 0 12px 0;
            }
            .mr-chapter-nav button {
              font-family: inherit !important;
              font-size: 14px !important;
              line-height: 1.2 !important;
              color: var(--mr-fg) !important;
              background: transparent !important;
              border: 1px solid var(--mr-fg) !important;
              border-radius: 999px !important;
              padding: 8px 20px !important;
              opacity: 0.9;
            }
            .mr-chapter-nav button + button {
              margin-left: 12px;
            }
            .mr-chapter-nav button:disabled {
              opacity: 0.3 !important;
            }
            ::selection {
              background: rgba(128, 128, 128, 0.35);
            }
            $boldRule
            $grayscaleRule
        """.trimIndent()
    }

    fun javascript(): String = """
        (function () {
          if (window.__mrInstalled) return;
          window.__mrInstalled = true;

          var lastReport = 0;
          window.addEventListener('scroll', function () {
            var now = Date.now();
            if (now - lastReport < 280) return;
            lastReport = now;
            var doc = document.documentElement;
            var max = Math.max(0, doc.scrollHeight - window.innerHeight);
            var ratio = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0;
            if (window.Android && Android.onScroll) {
              Android.onScroll(ratio);
            }
          }, { passive: true });

          var userMoved = false;
          window.addEventListener('touchstart', function () { userMoved = true; }, { passive: true });
          window.addEventListener('wheel', function () { userMoved = true; }, { passive: true });

          var swipeStartX = 0;
          var swipeStartY = 0;
          var swipeStartTime = 0;
          var swipeActive = false;
          var SWIPE_MIN_RATIO = 0.20;
          var SWIPE_MIN_PX = 56;
          var SWIPE_MAX_PX = 140;
          var SWIPE_FLICK_RATIO = 0.08;
          var SWIPE_FLICK_VELOCITY = 0.6;
          var SWIPE_VERTICAL_RATIO = 1.5;
          window.__mrHasPrev = false;
          window.__mrHasNext = false;

          document.addEventListener('touchstart', function (event) {
            if (event.touches.length !== 1) {
              swipeActive = false;
              return;
            }
            swipeStartX = event.touches[0].clientX;
            swipeStartY = event.touches[0].clientY;
            swipeStartTime = Date.now();
            swipeActive = true;
          }, { passive: true });

          document.addEventListener('touchend', function (event) {
            if (!swipeActive) return;
            swipeActive = false;
            var touch = event.changedTouches && event.changedTouches[0];
            if (!touch) return;
            var elapsed = Date.now() - swipeStartTime;
            var dx = touch.clientX - swipeStartX;
            var dy = touch.clientY - swipeStartY;
            var horizontal = Math.abs(dx);
            var vertical = Math.abs(dy);
            var velocity = elapsed > 0 ? horizontal / elapsed : 0;
            var distance = Math.min(
              SWIPE_MAX_PX,
              Math.max(SWIPE_MIN_PX, window.innerWidth * SWIPE_MIN_RATIO)
            );
            var flickDistance = window.innerWidth * SWIPE_FLICK_RATIO;
            var longEnough = horizontal >= distance;
            var quickFlick = horizontal >= flickDistance && velocity >= SWIPE_FLICK_VELOCITY;
            if (!longEnough && !quickFlick) return;
            if (horizontal <= vertical * SWIPE_VERTICAL_RATIO) return;
            if (dx < 0) {
              if (window.__mrHasNext && window.Android && Android.onNextChapter) {
                Android.onNextChapter();
              }
            } else if (window.__mrHasPrev && window.Android && Android.onPreviousChapter) {
              Android.onPreviousChapter();
            }
          }, { passive: true });

          document.addEventListener('touchcancel', function () {
            swipeActive = false;
          }, { passive: true });

          window.__mrRestore = function (ratio, fragment) {
            var apply = function () {
              var element = null;
              if (fragment) {
                try {
                  element = document.getElementById(fragment) || document.getElementsByName(fragment)[0];
                } catch (error) {
                  element = null;
                }
              }
              if (element) {
                element.scrollIntoView();
                return;
              }
              var doc = document.documentElement;
              var max = Math.max(0, doc.scrollHeight - window.innerHeight);
              window.scrollTo(0, max * Math.min(1, Math.max(0, ratio || 0)));
            };
            userMoved = false;
            requestAnimationFrame(function () {
              requestAnimationFrame(apply);
            });
            setTimeout(function () {
              if (!userMoved) apply();
            }, 700);
            setTimeout(function () {
              if (!userMoved) apply();
            }, 1400);
          };

          window.__mrUpdateStyle = function (css) {
            var style = document.getElementById('mr-style');
            if (style) style.textContent = css;
          };

          window.__mrApplyStyle = function (css) {
            var doc = document.documentElement;
            var max = Math.max(0, doc.scrollHeight - window.innerHeight);
            var ratio = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0;
            window.__mrUpdateStyle(css);
            requestAnimationFrame(function () {
              var nextDoc = document.documentElement;
              var nextMax = Math.max(0, nextDoc.scrollHeight - window.innerHeight);
              window.scrollTo(0, nextMax * ratio);
            });
          };

          window.__mrScrollToFragment = function (fragment) {
            if (!fragment) return;
            var element = null;
            try {
              element = document.getElementById(fragment) || document.getElementsByName(fragment)[0];
            } catch (error) {
              element = null;
            }
            if (element) element.scrollIntoView();
          };

          window.__mrScrollToRatio = function (ratio) {
            var doc = document.documentElement;
            var max = Math.max(0, doc.scrollHeight - window.innerHeight);
            window.scrollTo(0, max * Math.min(1, Math.max(0, ratio || 0)));
          };

          window.__mrSetChapterNav = function (chapterIndex, chapterCount) {
            var hasPrev = chapterIndex > 0;
            var hasNext = chapterIndex >= 0 && chapterIndex < chapterCount - 1;
            window.__mrHasPrev = hasPrev;
            window.__mrHasNext = hasNext;

            var nav = document.getElementById('mr-chapter-nav');
            if (!nav) {
              nav = document.createElement('div');
              nav.id = 'mr-chapter-nav';
              nav.className = 'mr-chapter-nav';

              var prev = document.createElement('button');
              prev.type = 'button';
              prev.textContent = '上一章';
              prev.addEventListener('click', function () {
                if (window.__mrHasPrev && window.Android && Android.onPreviousChapter) {
                  Android.onPreviousChapter();
                }
              });

              var next = document.createElement('button');
              next.type = 'button';
              next.textContent = '下一章';
              next.addEventListener('click', function () {
                if (window.__mrHasNext && window.Android && Android.onNextChapter) {
                  Android.onNextChapter();
                }
              });

              nav.appendChild(prev);
              nav.appendChild(next);
              (document.body || document.documentElement).appendChild(nav);
            }

            var buttons = nav.getElementsByTagName('button');
            if (buttons.length >= 2) {
              buttons[0].disabled = !hasPrev;
              buttons[1].disabled = !hasNext;
            }
            nav.style.display = chapterCount > 1 ? 'flex' : 'none';
          };

          window.__mrCaptureExcerpt = function () {
            var element = document.elementFromPoint(
              window.innerWidth / 2,
              Math.min(140, window.innerHeight / 3)
            );
            var blockTags = /^(P|DIV|LI|BLOCKQUOTE|TD|TH|H1|H2|H3|H4|H5|H6|SECTION|ARTICLE)$/;
            while (element && !(element.tagName && blockTags.test(element.tagName))) {
              element = element.parentElement;
            }
            var text = element && element.innerText
              ? element.innerText
              : (document.body ? document.body.innerText : '');
            return String(text || '').replace(/\s+/g, ' ').trim().slice(0, 120);
          };
        })();
    """.trimIndent()
}
