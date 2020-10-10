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
