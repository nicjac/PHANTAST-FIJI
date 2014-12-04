/*
 * PHANTAST - Plugin for FIJI
 * 
Copyright (c) 2013, Nicolas Jaccard

Department of Biochemical Engineering, UCL
Centre for Mathematics and Physics in the Life Sciences and Experimental Biology, UCL
The British Heart Foundation

All rights reserved.

Redistribution and use in source and binary forms, with or without 
modification, are permitted provided that the following conditions are 
met:

    * Redistributions of source code must retain the above copyright 
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright 
      notice, this list of conditions and the following disclaimer in 
      the documentation and/or other materials provided with the distribution
    * Neither the names of the University College London or British Heart Foundation nor the names 
      of its contributors may be used to endorse or promote products derived 
      from this software without specific prior written permission.
      
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.

*/
import ij.ImagePlus;
import ij.IJ;

import java.awt.*;

import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.DialogListener;
import ij.Macro;

import java.awt.Font;

import ij.process.ImageProcessor;
import ij.process.BinaryProcessor;
import ij.process.ByteProcessor;
import net.imglib2.Cursor;
import ij.gui.Roi;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.logic.BitType;
import net.imglib2.algorithm.gauss.Gauss;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.Type;

import java.util.Iterator;	

import net.imglib2.RandomAccess;
import ij.plugin.filter.ParticleAnalyzer;
import ij.measure.ResultsTable;
import ij.measure.Measurements;

import java.util.*; 
//import net.imglib2.script.math.fn.BinaryOperation;





@SuppressWarnings("deprecation")
public class PHANTAST_<T extends RealType<T> & NativeType<T>> implements ExtendedPlugInFilter,DialogListener  {
	protected ImagePlus inputImage;
	private ImagePlus previewImage;
	private double sigma = 1.2;
	private double epsilon = 0.03;
	private boolean doHaloCorrection = true;;
	private boolean cancel = false;
	private boolean ok = false;
	private boolean previewing = false;
	private boolean computeConfluency = false;

	private int nPasses; // Total number of passes
	private int pass; // Current pass
	private boolean outputSelection;
	private boolean outputMask;
	
	
	private String[] outputs = {"Binary mask", "Image with ROI"};
	int flags = DOES_32;
	int[][] projectionCones = new int[][] 
	{{1, 2, 8},
	 {2, 1, 3},
	 {3, 2, 4},
	 {4, 3, 5},
	 {5, 4, 6},
	 {6, 5, 7},
	 {7, 6, 8},
	 {8, 1, 7}};

	 int[][] directionOffsets2= new int[][]
	 {{0,1},    //EAST 1    	
         {-1,1},   //NORTH EAST 2 
         {-1,0},   //NORTH 3
         {-1,-1},  //NORTH WEST 4
         {0,-1},   //WEST 5
         {1,-1},   //SOUTH WEST 6 
         {1,0},    //SOUTH 7
         {1,1}};   //SOUTH EAST 8

         int[][] directionOffsets= new int[][]
	 {{1,0},    //EAST 1    	
         {1,-1},   //NORTH EAST 2 
         {0,-1},   //NORTH 3
         {-1,-1},  //NORTH WEST 4
         {-1,0},   //WEST 5
         {-1,1},   //SOUTH WEST 6 
         {0,1},    //SOUTH 7
         {1,1}};   //SOUTH EAST 8

	/**
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
	public int setup(String arg, ImagePlus imp) {
		//IJ.run("PHANTAST ", "sigma=1.20 epsilon=0.03 output=[Binary mask]");
		// does not handle RGB, since the wrapped type is ARGBType (not a RealType)
		return flags;
	}


	// Used to set number of calls(progress bar)
	public void setNPasses(int nPasses) {
		this.nPasses = nPasses;
		pass = 0;
	}

	/**
	 * As the class implements DialogListener, this method is called whenever a setting is changed
	 */
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {   
		sigma = (double)gd.getNextNumber();
		epsilon = (double)gd.getNextNumber();	
		doHaloCorrection = gd.getNextBoolean();
		computeConfluency = gd.getNextBoolean();
		outputSelection = gd.getNextBoolean();
		outputMask = gd.getNextBoolean();
	        previewing = gd.getPreviewCheckbox().getState();       
	        return true;
    	}

      	/**
        * Automatically called by ExtendedPlugInFilter, displays the setting dialog
        * When done, run(ImageProcessor) will be automatically called
        */
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		inputImage = imp;
		String options = Macro.getOptions();

		
		GenericDialog gd = new GenericDialog("PHANTAST Settings", IJ.getInstance());
		gd.addMessage("SEGMENTATION PARAMETERS",new Font("",Font.BOLD,12));
		gd.addMessage("Local contrast thresholding",new Font("",Font.ITALIC,12));
		gd.addNumericField("Sigma", sigma, 2);
		gd.addNumericField("Epsilon", epsilon, 2);
		gd.addMessage("Halo correction",new Font("",Font.ITALIC,12));
		gd.addCheckbox("Do halo correction",doHaloCorrection);
		gd.addMessage("OUTPUT OPTIONS",new Font("",Font.BOLD,12));
		gd.addMessage("Measurements",new Font("",Font.ITALIC,12));
		gd.addCheckbox("Compute confluency", false);
		gd.addMessage("Image output",new Font("",Font.ITALIC,12));
		gd.addCheckbox("Selection overlay on original image", false);
		gd.addCheckbox("New mask image", false);
		gd.addMessage("");
		gd.addPreviewCheckbox(pfr);
        	gd.addDialogListener(this);
		
		gd.showDialog();
		
		flags = IJ.setupDialog(imp, flags); //ask whether to process all slices of stack (if a stack)
		
		if(gd.wasOKed()){
	            ok = true;            
	            return flags;
	        }else{
	            ok = false;
	            flags = DONE;
	        }
	        if(gd.wasCanceled()){
	            cancel = true;
	            flags = DONE;
	        }else{
	            cancel = false;
	        }
	        
        	return flags;
	}

	/**
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
	public void run(ImageProcessor ip) {
		run(new ImagePlus(inputImage.getTitle(),ip.duplicate())); // Convert IP to ImagePlus
	}
	
	/**
	 * This should have been the method declared in PlugInFilter...
	 */
	public void run(ImagePlus image) {

	        if (null == image) {
	            IJ.error("PHANTAST", "No images are open.");
	            return;
	        }
	        
		pass++;
		
		String imageTitle;
		if(inputImage.getStack().getSize() >1) imageTitle = inputImage.getStack().getSliceLabel(pass);
		else imageTitle = inputImage.getTitle();
		
		// Convert the image to imglib2 Img format
		Img<T> img = ImagePlusAdapter.wrap(image.duplicate());

		// Apply local contrast filter
		final Img< T > localContrast = getLocalContrastImage(img, sigma);

		// Threshold the resulting image
		Img< UnsignedByteType > LCThresholded = thresholdImage(localContrast,epsilon);
		LCThresholded = removeSmallObjectsAndFillHoles(LCThresholded);

		if(doHaloCorrection) haloCorrection(LCThresholded,image);

		ImageProcessor ipPreview;

		ImagePlus LCThresholdedIP = ImageJFunctions.wrap(LCThresholded,"ResultImage");

		int previewWindows = 0;
		
		if(outputSelection)
		{
			IJ.run(LCThresholdedIP,"Convert to Mask", "method=Li background=Dark");
			IJ.runPlugIn(LCThresholdedIP,"ij.plugin.filter.ThresholdToSelection", "");
			Roi resultRoi = LCThresholdedIP.getRoi();
			inputImage.setRoi(resultRoi);	
			inputImage.show();
		}

		
		if(outputMask)
		{
			previewWindows++;
			
			ipPreview = LCThresholdedIP.duplicate().getProcessor();
			if(previewImage==null)
			{
				previewImage = new ImagePlus("PHANTAST - " + imageTitle,ipPreview);
				previewImage.show();
			}
			else previewImage.setProcessor(ipPreview);		
		}


		if(!previewing) {
			if(computeConfluency)
			{
				ResultsTable rt = ResultsTable.getResultsTable();

				rt.incrementCounter();
				rt.addLabel("ImageName", imageTitle);
				rt.addValue("Confluency",computeConfluency(LCThresholded));
		
				rt.show("Results");
			}
		}

		showProgress();

	}

	// Let IJ know about our progress
	void showProgress()
	{
		double percent = (double)pass/nPasses;
        	IJ.showProgress(percent);
	}

 	/**
 	 * Correct halo artifacts
 	 */
	void haloCorrection(Img<UnsignedByteType> imgToShrink, ImagePlus image)
	{
		Img< UnsignedByteType> LCThresholdedOutline = getOutlineImage(imgToShrink,false);

		//ImageJFunctions.show(LCThresholded);

		// Store the positions of outline pixels
		ArrayList<int[]> outlinePixels = getOutlinePixels(LCThresholdedOutline);

		// Get the direction image
		Img< UnsignedByteType > directionImage = getDirectionImage(image);
		//ImageJFunctions.show(directionImage,"Gradient Image");
		
		final RandomAccess< UnsignedByteType > r = directionImage.randomAccess();
		int[] currentPosition = new int[2];

		long[] dimDirectionImage = new long[2];
		directionImage.dimensions(dimDirectionImage);

		// Create an instance of an image factory
		ImgFactory< BitType > imgFactoryBit = new ArrayImgFactory< BitType >();
		
		// Create an Img with the same dimension as the input image
		final Img< BitType > consideredAsStartingPoint = imgFactoryBit.create(dimDirectionImage, new BitType() );

		boolean go = true;
		final RandomAccess< UnsignedByteType > rBinaryImage = imgToShrink.randomAccess();

		ArrayList<int[]> pixelsToProcess = new ArrayList<int[]>(outlinePixels);
		int count = 0;
		while(go)
		{
			++count;
			ArrayList<int[]> toAddToQueue = new ArrayList<int[]>();
			ArrayList<int[]> toBeRemoved = new ArrayList<int[]>();
			
			for(int i=0;i<pixelsToProcess.size();i++)
			{
					
				boolean validPath = false;
	
				// Get current position index
				currentPosition = (int[])pixelsToProcess.get(i);
	
				r.setPosition( (int)currentPosition[0], 0 );
	            		r.setPosition( (int)currentPosition[1], 1 );
	
	            		final UnsignedByteType currentDirection = (UnsignedByteType)r.get();
	
				int[] currentDirectionCone = projectionCones[(int)currentDirection.getInteger()];
	
				//IJ.log("" + currentPosition[0]);
	
				final RandomAccess< BitType > rStartPoint = consideredAsStartingPoint.randomAccess();
				rStartPoint.setPosition( (int)currentPosition[0], 0 );
	            		rStartPoint.setPosition( (int)currentPosition[1], 1 );
				int startFlag = (int) rStartPoint.get().getInteger();
				if(startFlag==0)
	        		{
					// Need to add 'consideredStartingPoint' stuff
					if((currentPosition[0] >0) & (currentPosition[1] >0) & (currentPosition[0] < dimDirectionImage[0]-1) & (currentPosition[1] < dimDirectionImage[1]-1))
					{
						// Everything is an object apparently!
						// Set this position as already visisted
						final BitType t = rStartPoint.get();
	            				t.setOne();  
	
	            				for(int k=0;k<3;k++) {
							int[] directionOffset = directionOffsets[currentDirectionCone[k]-1];
							int[] nextPosition = new int[2];
	
							nextPosition[0] = currentPosition[0] + directionOffset[0];
							nextPosition[1] = currentPosition[1] + directionOffset[1];					
							
							rBinaryImage.setPosition((int)nextPosition[0],0);
							rBinaryImage.setPosition((int)nextPosition[1],1);
							//IJ.log("" + (int)nextPosition[0]);
							//IJ.log("" + (int)nextPosition[1]);
							if(rBinaryImage.get().getInteger()==255)
							{		
					                        if((nextPosition[0] >=1) & (nextPosition[1] >=1) & (nextPosition[0] <= dimDirectionImage[0]) & (nextPosition[1] <= dimDirectionImage[1]))
					                        {
					                        	validPath = true;
					                        	toAddToQueue.add(nextPosition);
					                        }
							}
	            				}
					}
	        		}
	
	        		if(validPath) {
	        			toBeRemoved.add((int[])currentPosition);
	        		}
			
			}

			for(int j=1; j<toBeRemoved.size();j++){
				rBinaryImage.setPosition((int)toBeRemoved.get(j)[0],0);
				rBinaryImage.setPosition((int)toBeRemoved.get(j)[1],1);
				final UnsignedByteType t = rBinaryImage.get();
            			t.set(0);  
			}
			//IJ.log(""+pixelsToProcess.size());

			pixelsToProcess = new ArrayList<int[]>(toAddToQueue);
			
			if(pixelsToProcess.size()==0)
			{
				go = false;
			}
		}
	}

	double computeConfluency (Img<UnsignedByteType> img)
	{
		Cursor c = img.cursor();		

		double onPixels = 0;
		double offPixels = 0;
		
		while (c.hasNext())
		{
			IntegerType t = (IntegerType)c.next();
			
			if(t.getInteger()==255)
			{
				onPixels++;
			}
			else offPixels++;
		}

		//IJ.log("" + ((int[])outlinePixelsIndices.get(12))[0]);
		return (double)onPixels/(onPixels+offPixels);
	}

	Img<UnsignedByteType> removeSmallObjectsAndFillHoles(Img<UnsignedByteType> img){
		ResultsTable table = new ResultsTable();
		//# Create a ParticleAnalyzer, with arguments:
		//# 1. options (could be SHOW_ROI_MASKS, SHOW_OUTLINES, SHOW_MASKS, SHOW_NONE, ADD_TO_MANAGER, and others; combined with bitwise-or)
		//# 2. measurement options (see [http://rsb.info.nih.gov/ij/developer/api/ij/measure/Measurements.html Measurements])
		//# 3. a ResultsTable to store the measurements
		//# 4. The minimum size of a particle to consider for measurement
		//# 5. The maximum size (idem)
		//# 6. The minimum circularity of a particle
		//# 7. The maximum circularity
		
		// Remove small particles
		ParticleAnalyzer pa = new ParticleAnalyzer(ParticleAnalyzer.SHOW_MASKS, Measurements.AREA, table, 100, Double.POSITIVE_INFINITY, 0.0, 1.0);
		pa.setHideOutputImage(true);
		ImagePlus tmp = ImageJFunctions.wrap(img,"Tmp");
		pa.analyze(tmp);

		// Remove holes
		ImagePlus tmp2 = pa.getOutputImage();
		pa = new ParticleAnalyzer(ParticleAnalyzer.SHOW_MASKS, Measurements.AREA, table, 25, Double.POSITIVE_INFINITY, 0.0, 1.0);
		pa.setHideOutputImage(true);
		pa.analyze(tmp2);

		BinaryProcessor binaryProc = new BinaryProcessor(new ByteProcessor(pa.getOutputImage().getChannelProcessor(),false)); 
		binaryProc.invert();
		ImagePlus tmp3 = new ImagePlus("NoHolesorSmallObjects", binaryProc); 
		return ImageJFunctions.wrap(tmp3);
 
	}

	Img<UnsignedByteType> getOutlineImage(Img<UnsignedByteType> img, boolean fillImage)
	{
		// Create an ImagePlus version of the local contrast image
		// This is done as I don't know yet how to do outline calculation using imglib2 directly
		ImagePlus Img_IP = ImageJFunctions.wrap(img,"Thresholded");
		//LCThresholded_IP.show();
		
		if(fillImage) IJ.runPlugIn(Img_IP,"ij.plugin.filter.ThresholdToSelection", "");
		
		// Create a binary processor which allows to call outline() directly
		// Reminder: ImagePlus contain processor (not the other way around!)
		BinaryProcessor binaryProc = new BinaryProcessor(new ByteProcessor(Img_IP.getChannelProcessor(),false)); 
		
		// Weird but need for some reason
		binaryProc.invert();
		binaryProc.outline(); 
		binaryProc.invert();

		// Create a new ImagePlus object from the processor
		ImagePlus outlineImage_IP = new ImagePlus("outline", binaryProc); 
		
		//LCThresholdedOutline_IP.show();

		// Convert back to imglib2 Img format
		Img< UnsignedByteType > outlineImage = ImageJFunctions.wrap(outlineImage_IP);

		return outlineImage;
	}

	Img<T> getImageWithOverlay(Img<T> img, ArrayList<int[]> maskPixels, double outlineValue)
	{
		Img<T> tmpImage = img.copy();
		
		final RandomAccess< T > r = tmpImage.randomAccess();

		int[] currentPosition = new int[2];
		
		for(int i=0;i<maskPixels.size();i++)
		{
			// Get current position index
			currentPosition = (int[])maskPixels.get(i);
	
			r.setPosition( (int)currentPosition[0], 0 );
	    		r.setPosition( (int)currentPosition[1], 1 );

	    		final RealType t = r.get();
	    		t.setReal(outlineValue);
		}

		return tmpImage;
	}

	public Img<T> getLocalContrastImage(Img<T> img, double sigma_)
	{
		final double[] sigma = new double[ img.numDimensions() ];
		
		for ( int d = 0; d < sigma.length; ++d )
		    sigma[ d ] = sigma_;

		final Img< T > imageRooted = squareImage(img);

		final Img< T > imageRootedConvolved = Gauss.inFloat( sigma, imageRooted );

		final Img< T > imageConvolved = Gauss.inFloat( sigma, img );

		final Img< T > imageConvolvedRooted = squareImage(imageConvolved);

		final Img< T > localContrast = divideImages(squareRootImage(subtractImages(imageRootedConvolved,imageConvolvedRooted)),imageConvolved);

		return localContrast;
	}
	
	public Img<UnsignedByteType> getDirectionImage(ImagePlus img)
	{
		float[][] kirsch = new float[][] { {-3,-3,5,-3,0,5,-3,-3,5},
						   {-3,5,5,-3,0,5,-3,-3,-3},
					   	   {5,5,5,-3,0,-3,-3,-3,-3},					   	   
					   	   {5,5,-3,5,0,-3,-3,-3,-3},					   	   
					   	   {5,-3,-3,5,0,-3,5,-3,-3},					   	   
					   	   {-3,-3,-3,5,0,-3,5,5,-3},					   	   
					   	   {-3,-3,-3,-3,0,-3,5,5,5},					   	   
					   	   {-3,-3,-3,-3,0,5,-3,5,5}};


		Img<UnsignedByteType>[] responseImages = new Img[8];

		for(int i = 0; i < 8; i++)
		{
			ImagePlus tmp = img.duplicate();
			tmp.getChannelProcessor().convolve(kirsch[i],3,3);
			responseImages[i] = ImagePlusAdapter.wrap(tmp);
		}

		Cursor c1 = responseImages[0].cursor();
		Cursor c2 = responseImages[1].cursor();
		Cursor c3 = responseImages[2].cursor();
		Cursor c4 = responseImages[3].cursor();
		Cursor c5 = responseImages[4].cursor();
		Cursor c6 = responseImages[5].cursor();
		Cursor c7 = responseImages[6].cursor();
		Cursor c8 = responseImages[7].cursor();



		long[] dim = new long[2];
		responseImages[0].dimensions(dim);
		
		//final Img< T > directionImage = imgFactory.create( responseImages[0], responseImages[0].firstElement() );

		ImgFactory< UnsignedByteType > imgFactory = new ArrayImgFactory< UnsignedByteType >();
		final Img<UnsignedByteType> directionImage = imgFactory.create(dim,new UnsignedByteType());
		//final Img< T > directionImage = imgFactory.create(dim,new RealType());
		
		Cursor cDirection = directionImage.cursor();
		
		while (c1.hasNext())
		{
			RealType[] t = new RealType[8]; 
			t[0] = (RealType)c1.next();
			t[1] = (RealType)c2.next();
			t[2] = (RealType)c3.next();
			t[3] = (RealType)c4.next();
			t[4] = (RealType)c5.next();
			t[5] = (RealType)c6.next();
			t[6] = (RealType)c7.next();
			t[7] = (RealType)c8.next();

			RealType tDirection = (UnsignedByteType)cDirection.next();

			float max = 0f;
			int kernelId = 0;
			for(int i = 0; i < 8; i++)
			{
				float currentValue = t[i].getRealFloat();
				if(i==0) 
				{
					max = currentValue;
					kernelId = 0;
				}
				else if(currentValue>max) 
				{
					max = currentValue;
					kernelId = i;
				}
			}

			tDirection.setReal(kernelId);
			
		}

		return directionImage;
	}

	public < T extends RealType< T > & NativeType< T > > ArrayList<int[]> getOutlinePixels(Img<T> img)
	{
		ArrayList<int[]> outlinePixelsIndices = new ArrayList();

		Cursor c = img.cursor();		

		while (c.hasNext())
		{
			IntegerType t = (IntegerType)c.next();
			int[] position = new int[2];
			
			if(t.getInteger()==255)
			{
				c.localize(position);
				outlinePixelsIndices.add(position);
			}
		}

		//IJ.log("" + ((int[])outlinePixelsIndices.get(12))[0]);
		return outlinePixelsIndices;
	}

	public Img<UnsignedByteType> thresholdImage(Img<T> img, double thresholdValue)
	{
		// Create an instance of an image factory
		ImgFactory< UnsignedByteType > imgFactory = new ArrayImgFactory< UnsignedByteType >();

		long[] dim = new long[2];
		img.dimensions(dim);
		
		// Create an Img with the same dimension as the input image
		final Img< UnsignedByteType > tmpImage = imgFactory.create( dim, new UnsignedByteType() );
	
		// Create a cursor for both images
		Cursor c1 = img.cursor();
		Cursor c2 = tmpImage.cursor();
		
		// do for all pixels
		while ( c1.hasNext() )
		{
			// get value of both imgs
			RealType t1 = (RealType)c1.next();
			RealType t2 = (RealType)c2.next();

			// overwrite img1 with the result

			if(t1.getRealFloat() > thresholdValue)
			{
				t2.setReal(255);
			}
			else
			{
				t2.setReal(0);
			}
		}

		return tmpImage;		
	}
	
	public < T extends RealType< T > & NativeType< T > > void scaleImage(Img<T> img)
	{
		// create two empty variables
		T min = img.firstElement().createVariable();
		T max = img.firstElement().createVariable();
		
		// compute min and max of the Image
		computeMinMax( img, min, max );

		//IJ.log( "minimum Value (img): " + min );
		//IJ.log( "maximum Value (img): " + max );
		//write("lol");

		// Create a cursor for both images
		Cursor c1 = img.cursor();
		
		// do for all pixels
		while ( c1.hasNext() )
		{
			// get value of both imgs
			RealType t1 = (RealType)c1.next();

			// overwrite img1 with the result
			t1.setReal( t1.getRealFloat() / max.getRealFloat() );
		}
	}

	
	public < T extends Comparable< T > & Type< T > > void computeMinMax(final Iterable< T > input, final T min, final T max )
	{
		// create a cursor for the image (the order does not matter)
		final Iterator< T > iterator = input.iterator();
		
		// initialize min and max with the first image value
		T type = iterator.next();
		
		min.set( type );
		max.set( type );
		
		// loop over the rest of the data and determine min and max value
		while ( iterator.hasNext() )
		{
		    // we need this type more than once
		    type = iterator.next();
		
		    if ( type.compareTo( min ) < 0 )
		        min.set( type );
		
		    if ( type.compareTo( max ) > 0 )
		        max.set( type );
		}
	}
 
	
	public static< T extends RealType< T > & NativeType< T > > Img<T> squareImage(Img<T> img)
	{
		return multiplyImages(img,img);
	}

	public static< T extends RealType< T > & NativeType< T > > Img<T> multiplyImages(Img<T> img1,Img<T> img2)
	{
		// Create an instance of an image factory
		ImgFactory< T > imgFactory = new ArrayImgFactory< T >();

		// Create an Img with the same dimension as the input image
		final Img< T > tmpImage = imgFactory.create( img1, img1.firstElement() );
		
		// Create a cursor for both images
		Cursor c1 = img1.cursor();
		Cursor c2 = img2.cursor();
		Cursor c3 = tmpImage.cursor();
		
		// do for all pixels
		while ( c1.hasNext() )
		{
			// get value of both imgs
			RealType t1 = (RealType)c1.next();
			RealType t2 = (RealType)c2.next();
			RealType t3 = (RealType)c3.next();

			// overwrite img1 with the result
			t3.setReal( t1.getRealFloat() * t2.getRealFloat() );
		}

		return tmpImage;
	}

	public static< T extends RealType< T > & NativeType< T > > Img<T> divideImages(Img<T> img1,Img<T> img2)
	{
		// Create an instance of an image factory
		ImgFactory< T > imgFactory = new ArrayImgFactory< T >();

		// Create an Img with the same dimension as the input image
		final Img< T > tmpImage = imgFactory.create( img1, img1.firstElement() );
		
		// Create a cblurGaussianblurGaussianursor for both images
		Cursor c1 = img1.cursor();
		Cursor c2 = img2.cursor();
		Cursor c3 = tmpImage.cursor();
		
		// do for all pixels
		while ( c1.hasNext() )
		{
			// get value of both imgs
			RealType t1 = (RealType)c1.next();
			RealType t2 = (RealType)c2.next();
			RealType t3 = (RealType)c3.next();

			if(t2.getRealFloat()>0)
			{
				// overwrite img1 with the result
				t3.setReal( t1.getRealFloat() / t2.getRealFloat() );
			} else {
				t3.setReal(0);
			}
			
		}

		return tmpImage;
	}

	public static< T extends RealType< T > & NativeType< T > > Img<T> subtractImages(Img<T> img1,Img<T> img2)
	{
		// Create an instance of an image factory
		ImgFactory< T > imgFactory = new ArrayImgFactory< T >();

		// Create an Img with the same dimension as the input image
		final Img< T > tmpImage = imgFactory.create( img1, img1.firstElement() );
		
		// Create a cursor for both images
		Cursor c1 = img1.cursor();
		Cursor c2 = img2.cursor();
		Cursor c3 = tmpImage.cursor();
		
		// do for all pixels
		while ( c1.hasNext() )
		{
			// get value of both imgs
			RealType t1 = (RealType)c1.next();
			RealType t2 = (RealType)c2.next();
			RealType t3 = (RealType)c3.next();

			if(t2.getRealFloat()>0)
			{
				// overwrite img1 with the result
				t3.setReal( t1.getRealFloat() - t2.getRealFloat() );
			} else {
				t3.setReal(0);
			}
			
		}

		return tmpImage;
	}

	public static< T extends RealType< T > & NativeType< T > > Img<T> squareRootImage(Img<T> img)
	{
		// Create an instance of an image factory
		ImgFactory< T > imgFactory = new ArrayImgFactory< T >();

		// Create an Img with the same dimension as the input image
		final Img< T > tmpImage = imgFactory.create( img, img.firstElement() );
		
		// Create a cursor for both images
		Cursor c1 = img.cursor();
		Cursor c2 = tmpImage.cursor();
		
		// do for all pixels
		while ( c1.hasNext() )
		{
			// get value of both imgs
			RealType t1 = (RealType)c1.next();
			RealType t2 = (RealType)c2.next();

			// overwrite img1 with the result
			//t2.setReal( Math.sqrt(t1.getRealFloat()));
			t2.setReal( Math.sqrt(t1.getRealFloat()));
			//t2.setReal( t1.getRealFloat());
		}

		return tmpImage;
	}
	
}

