Paperant
========

[PDF Library choice](https://hub.alfresco.com/t5/alfresco-content-services-blog/pdf-rendering-engine-performance-and-fidelity-comparison/ba-p/287618):
| Engine                  | License                                   | Notes |
| Ghostscript             | AGPLv3 or Commercial                      | Full PostScript interpreter. Can also handle PDF files. |
| MuPDF                   | AGPLv3 or Commercial                      | PDF, XPS and EPUB rendering engine based on the modern high performance Fitz graphics engine |
| Adobe PDF Library SDK   | Commercial                                | Original Adobe PDF engine. |
| Foxit SDK               | Commercial                                | Engine behind the Foxit PDF reader products. Fork released under BSD license as Pdfium by Google. |
| Pdfium                  | BSD style                                 | Engine behind PDF Plug-In in Chrome. Fork of the Foxit SDK. |
| Poppler                 | GPLv2 or GPLv3                            | Fork of XPdf |
| Xpdf                    | GPLv2 or Commercial                       | PDF viewer for X-Windows & PDF Rasterizer for all platforms (pdftopng). | 
| GnuPDF                  | GPLv3                                     | |
| PDFBox 2.0              | Apache 2.0                                | |
| Sejda                   | AGPLv3 or Commercial                      | |
| IcePDF                  | Apache 2.0 and Commercial Pro version     | |
| Aspose PDF              | Commercial                                | |

Go with MuPDF.

Function calls:
`com.artifex.mupdf.fitz.android.AndroidDrawDevice`
`com.artifex.mupdf.fitz.Document`

Flow:
- `PageView::onSizeChanged`
 - `DocumentActivity::onPageViewSizeChanged`
  - `DocumentActivity::openDocument`
   + `Document::openDocument`
   - `DocumentActivity::loadDocument`
    + `// if reflow: Document::layout`
    + `Document::countPages
    - `DocumentActivity::loadPage`
     + `Document::loadPage`
     + `AndroidDrawDevice::fitPage`
     + `AndroidDrawDevice::fitPageWidth`
     + `AndroidDrawDevice::drawPage` !!
     - `PageView::setBitmap` -> `View::invalidate` -> `PageView::onDraw`
      + `Canvas::drawBitmap`

## Asynchrony issues

The naive way is to render a page each time when user turns the page.
The problem is that, when the user span a large range of pages,
the time span between two adjacent turning of page is hardly ever sufficient
for rendering.

Current solution is to use a background rendering thread equipped with a stack of page numbers.
When the user requests a new page, it is pushed on top of the stack.
The previous top page of the stack is wasted (which could be mitigated by the use of blurred cache),
but once it is finished, the current page starts to be processed.

But it takes a ton of work to convince oneself that the wasted rendering would not jeopardize the logic.
It is also reasonable to assume that a reasonable user do not operate too often when s/he has a proper task to achieve.

The other way, which is definitely the better way in my opinion, is to really kill the unused rendering thread.
Unfortunately, java do not support this, and there are random justifications for this out there.
Fortunately, pthread has this, and android ndk supports pthread.
That would however require additional efforts to write c code and porting. 

command used for building the native library for unit test on host:
`gcc -shared -fPIC -g -o app/build/intermediates/stripped_native_libs/debug/out/lib/x86_64/libkillable-thread.so -I/usr/lib/jvm/java-8-openjdk/include -I/usr/lib/jvm/java-8-openjdk/include/linux app/src//main//cpp//killable-thread.c`

<https://github.com/ArtifexSoftware/mupdf-android-appkit/blob/37d908ae035dc7275983666690638467fb717ccd/solib/library/src/mupdf/java/com/artifex/solib/MuPDFPage.java#L293>
