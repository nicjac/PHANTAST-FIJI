import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import java.awt.image.IndexColorModel;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.gui.GenericDialog;
import ij.gui.DialogListener;
import ij.WindowManager;
import ij.io.FileInfo;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.GaussianBlur;
import ij.process.FloatProcessor;
import java.awt.*;
import ij.*;

public class AFI_ implements PlugIn,DialogListener  {
	protected ImagePlus image;

	private double overlayOpacity;
	private boolean previewing;
	private ImagePlus rawImage;
	private ImagePlus UVImage;
	private ImagePlus maskImage;

	private ImagePlus resultImage;


	/**
	 * As the class implements DialogListener, this method is called whenever a setting is changed
	 */
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {    
	        rawImage = (WindowManager.getImage(gd.getNextChoiceIndex() + 1)).duplicate();
	        int maskChoice = gd.getNextChoiceIndex();

	        if(maskChoice == 0) {
	        	//Make mask from ROI
	        } 
	        else
	        {
        		maskImage = (WindowManager.getImage(maskChoice).duplicate());
	        }
        	UVImage = (WindowManager.getImage(gd.getNextChoiceIndex() + 1)).duplicate();  
        	overlayOpacity = gd.getNextNumber();
        	previewing = gd.getNextBoolean();

        	if(previewing) run("");
        		
	        return true;
    	}

      	/**
        * Automatically called by ExtendedPlugInFilter, displays the setting dialog
        * When done, run(ImageProcessor) will be automatically called
        * The ROI/image select stuff was adapted from / src-plugins / Colocalisation_Analysis / src / main / java / Coloc_2.java
        */
	public boolean showDialog() {
		
		GenericDialog gd = new GenericDialog("PHANTAST Settings", IJ.getInstance());

		// get IDs of open windows
	        int[] windowList = WindowManager.getIDList();
	        // if there are less than 2 windows open, cancel
	        if (windowList == null || windowList.length < 2) {
	            IJ.showMessage("At least 2 images must be open!");
	            return false;
	        }
		String[] titles = new String[windowList.length];
	        /* the masks and ROIs array needs three more entries than
	         * windows to contain "none", "ROI ch 1" and "ROI ch 2"
	         */
	        String[] roisAndMasks= new String[windowList.length + 1];

	        roisAndMasks[0]="ROI(s) in raw image";

	 
	        // go through all open images and add them to GUI
	        for (int i=0; i < windowList.length; i++) {
	            ImagePlus imp= WindowManager.getImage(windowList[i]);
	            if (imp != null) {
	                titles[i] = imp.getTitle();
	                roisAndMasks[i + 1] =imp.getTitle();
	            } else {
	                titles[i] = "";
	            }
	        }

	        int index1 = 0;
	        int index2 = 0;
	        int indexMask = 0;
	 
	        gd.addChoice("Raw image", titles, titles[index1]);
	        gd.addChoice("Cells segmentation mask", roisAndMasks, roisAndMasks[indexMask]);
	        gd.addChoice("Fluorescence image", titles, titles[index2]);
		gd.addSlider("Overlay opacity",0,1,0.6);
		gd.addCheckbox("Preview", false);
        	gd.addDialogListener(this);
		
		gd.showDialog();
		
		if(gd.wasOKed()){
	            return true;
	        }else{
	            return false;
	        }
	}

	FileInfo getLUTvalues(int color)
	{
		FileInfo fi = new FileInfo();

		fi.reds = new byte[256]; 
		fi.greens = new byte[256]; 
		fi.blues = new byte[256];
		
		for (int i=0; i<256; i++) {
		    if ((color&4)!=0)
		        fi.reds[i] = (byte)i;
		    if ((color&2)!=0)
		        fi.greens[i] = (byte)i;
		    if ((color&1)!=0)
		        fi.blues[i] = (byte)i;
		    if(color==0)
		    {
		    	fi.reds[i] = (byte)0;
		    	fi.greens[i] = (byte)0;
		    	fi.blues[i] = (byte)0;
		    }
		}

		return fi;
	}

	public void run(String arg) {
		boolean dialogState = false;
		if(!previewing) 
		{
			dialogState = showDialog();
		}
		else generateAFI("");

		if(dialogState) generateAFI("");
	}

	public void generateAFI(String arg) {
		if(resultImage == null)
		{
			resultImage = rawImage.duplicate();
			resultImage.show();
		} else // If result window already up we refresh it
		{
			resultImage.setProcessor(rawImage.getProcessor());
		}

		resultImage.setOverlay(null);
		
		FloatProcessor green = UVImage.getProcessor().toFloat(1, null);
		
		ImagePlus greenIP = new ImagePlus("Fluorescent image",green);

		GaussianBlur gb = new GaussianBlur();
		gb.blurGaussian(greenIP.getProcessor(),2,2,0.02);

		ImagePlus greenMask = greenIP.duplicate();
		greenMask.getProcessor().setThreshold(24, 24, ImageProcessor.NO_LUT_UPDATE);
		IJ.run(greenMask, "Convert to Mask", "");

		//greenMask.show();
		//LCThresholdedIP.show();
		ImageCalculator ic = new ImageCalculator();
   		ImagePlus positiveMask = ic.run("AND create", maskImage, greenMask);

		ImagePlus negativeMask = ic.run("OR create", maskImage, greenMask);

		ImagePlus backgroundMask = maskImage.duplicate();
		backgroundMask.getProcessor().invert();

		// Generate the LUT
		// Stolen from / src / main / java / ij / plugin / LutLoader.java
		// Red = 4, green = 2, blue = 1, black = 0;
		// Dumb object just to get LUT values
		FileInfo fi = new FileInfo();

		fi=getLUTvalues(2);
		IndexColorModel cm = new IndexColorModel(8, 256, fi.reds, fi.greens, fi.blues);
		positiveMask.getProcessor().setColorModel(cm);

		fi=getLUTvalues(4);
		cm = new IndexColorModel(8, 256, fi.reds, fi.greens, fi.blues);
		negativeMask.getProcessor().setColorModel(cm);

 		//fi=getLUTvalues(0);
 		fi.reds = new byte[256]; 
		fi.greens = new byte[256]; 
		fi.blues = new byte[256];

		fi.reds[0] = (byte)0;
		fi.blues[0] = (byte)0;
		fi.greens[0] = (byte)0;

		fi.reds[255] = (byte)5;
		fi.blues[255] = (byte)5;
		fi.greens[255] = (byte)5;
		cm = new IndexColorModel(8, 256, fi.reds, fi.greens, fi.blues);
		backgroundMask.getProcessor().setColorModel(cm);               
                
		// Userful ROI source http://fiji.sc/cgi-bin/gitweb.cgi?p=ImageJA.git;a=blob;f=src/main/java/ij/plugin/OverlayCommands.java;h=2e560ced45035d86ef7a06a0b2cb154164a758bc;hb=refs/heads/master
		Overlay overlayList = resultImage.getOverlay();
		if (overlayList==null) overlayList = new Overlay();
		
		ImageRoi roi = new ImageRoi(0, 0, negativeMask.getProcessor());
		roi.setZeroTransparent(true);
		roi.setOpacity(overlayOpacity);
   		overlayList.add(roi);

   		roi = new ImageRoi(0, 0, positiveMask.getProcessor());
		roi.setZeroTransparent(true);
		roi.setOpacity(overlayOpacity);
   		overlayList.add(roi);

   		roi = new ImageRoi(0, 0, backgroundMask.getProcessor());
		roi.setZeroTransparent(true);
		roi.setOpacity(overlayOpacity);
   		overlayList.add(roi);
   		
            	resultImage.setOverlay(overlayList);

	}
}