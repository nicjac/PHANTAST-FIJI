PHANTAST
========

The phase contrast microscopy segmentation toolbox (PHANTAST) is a collection of open-source algorithms and tools for the processing of phase contrast microscopy (PCM) images. It was developed at [University College London](http://www.ucl.ac.uk)'s [department of Biochemical Engineering](http://www.ucl.ac.uk/biochemeng) and [CoMPLEX](http://www.ucl.ac.uk/complex).

With the ultimate goal of being plateform agnostic, PHANTAST is currently available as a standalone GUI, MATLAB code, and FIJI plugin.

**IMPORTANT: You are currently browsing the PHANTAST for FIJI plugin. If you are interested in  PHANTAST for MATLAB or the standalone GUI application (offering similar functionality), please naviguate to the [corresponding repository](https://github.com/nicjac/PHANTAST-MATLAB).**

PHANTAST was first described in an [open-access paper](http://onlinelibrary.wiley.com/doi/10.1002/bit.25115/abstract) where it was used to non-invasively monitor proliferation, cell death, growth arrest and morphologyical changes in adherent cell cultures based on phase contrast microscopy images.

  * **Microscope users / researchers**: get the most of your phase contrast microscopy images and start generating quantitative data right away using our graphical user interface tool that does not require any image-processing knowledge
  * **Developers**: study, re-use and/or improve on our segmentation algorithm. We are keen on seeing what you can do with it!

Getting started
---------------

  * Head to the [release page](https://github.com/nicjac/PHANTAST-FIJI/releases) and download the latest release of the plugin. Alternatively, you can checkout the code and build it yourself.
  * The wiki contains a [tutorial to get you started with PHANTAST for FIJI](https://github.com/nicjac/PHANTAST-FIJI/wiki/PHANTAST-FIJI-plugin-tutorial)
  * You can get in touch with us through various means:
    * Use the [issue page](https://github.com/nicjac/PHANTAST-FIJI/issues) to report problems with the plugin
    * Head to the [getting in touch page](https://github.com/nicjac/PHANTAST-FIJI/wiki/Getting-in-touch) for more traditional contact means  

More about PHANTAST
-------------------

![alt text](https://github.com/nicjac/phantast/blob/gh-pages/images/Example.png "Example of PCM image segmentation using PHANTAST")

Generating quantitative data from PCM images is generally a tedious experience. Manual processing is time-consuming and error-prone while the use of general purpose image processing software package can be frustrating due to their complexity and the need to tweak parameters.

Clearly, PCM image processing is currently much more difficult than it should be. PHANTAST was developed by researchers who are in the laboratory and understand the need for a convenient tool that delivers uncompromising performance. 

Key features of PHANTAST:
  * PHANTAST enables culture confluency determination and cell density estimation (even for colony-forming cell lines) based on unlabelled PCM images.
  * High performance. PHANTAST is based on image processing algorithm specifically developed with PCM images features in mind, more specifically the low contrast between the cells and the background, and halo artefacts surrouding cellular objects.
  * It is free. Do not spend a large portion of your research budget on software license. 
  * It is open. Unlike most commercial products that adopt a black box model, PHANTAST is open and you are encouraged to delve into the code. See something wrong? Think a feature should be implemented? Let us know and join our effort!
  * Consistency. PHANTAST has been thoroughly validated using a variety of cell lines, imaging protocols and microscope models. 
  * Choice. PHANTAST is available as MATLAB code, as a standalone GUI tool and as a FIJI plugin. 
