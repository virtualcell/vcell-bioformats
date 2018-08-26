package org.vcell.bioformats;

/*
 * Copyright (C) 1999-2011 University of Connecticut Health Center
 *
 * Licensed under the GPL version 2 License (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *  http://www.opensource.org/licenses/GPL-2.0
 */

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.vcell.util.Extent;
import org.vcell.util.ISize;
import org.vcell.util.Origin;

import cbit.image.ImageSizeInfo;
import cbit.vcell.VirtualMicroscopy.BioformatsImageDatasetReader;
import cbit.vcell.VirtualMicroscopy.HiddenNonImageFile;
import cbit.vcell.VirtualMicroscopy.ImageDataset;
import cbit.vcell.VirtualMicroscopy.ImageDatasets;
import cbit.vcell.VirtualMicroscopy.UShortImage;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.UnknownFormatException;
import loci.formats.gui.AWTImageTools;
import loci.formats.gui.BufferedImageReader;
import loci.formats.in.ZeissLSMReader;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.meta.MetadataStore;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.units.unit.Unit;


public class BioFormatsImageDatasetReaderFrm {
	
	public static final boolean BIO_FORMATS_DEBUG = false;
	
	public BioFormatsImageDatasetReaderFrm(){
//		LogTools.setDebug(true);
//		LogTools.setDebugLevel(5);
	}
	
	public static void main(String[] args) {
		try {
			if(args[0].toLowerCase().equals(BioformatsImageDatasetReader.IMAGEINFO/*"imageinfo"*/)) {
				if(args.length != 2 && args.length != 3) {
					throw new IllegalArgumentException("Expecting args for 'imageinfo' filename [(int)forceZ]");
				}
				String fileName = args[1];
				Integer forceZSize = (args.length == 3?Integer.parseInt(args[2]):null);
				ImageSizeInfo imageSizeInfo = getImageSizeInfo(fileName, forceZSize);
				JAXBContext jaxbContext = JAXBContext.newInstance(new Class[] {ImageSizeInfo.class});
				String s = createXML(imageSizeInfo, jaxbContext);
				System.out.println(createXmlDelimiter(false)+s+createXmlDelimiter(true));
			}else if(args[0].toLowerCase().equals(BioformatsImageDatasetReader.IMAGEDATA/*"imagedata"*/)) {
				if(args.length != 2) {
					throw new IllegalArgumentException("Expecting exactly 2 args for 'imagedata'");
				}
				String fileName = args[1];
				ImageDataset imageDataset = readImageDataset(fileName);
				JAXBContext jaxbContext = JAXBContext.newInstance(new Class[] {ImageDataset.class});
				String s = createXML(imageDataset, jaxbContext);
				System.out.println(createXmlDelimiter(false)+s+createXmlDelimiter(true));
			}else if(args[0].toLowerCase().equals(BioformatsImageDatasetReader.IMAGEDATACHAN/*"imagedatachan"*/)) {
				if(args.length != 5) {
					throw new IllegalArgumentException("Expecting exactly 5 args for 'imagedatachannels'");
				}
				String fileName = args[1];
				//String imageID, boolean bMergeChannels,Integer timeIndex,ISize resize
				boolean bMergeChannels = Boolean.parseBoolean(args[2]);
				Integer timeIndex = (args[3].toLowerCase().equals("null")?null:Integer.parseInt(args[3]));
				ISize resize = null;
				if(!args[4].toLowerCase().equals("null")) {
					StringTokenizer st = new StringTokenizer(args[4], ",");
					resize = new ISize(Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()));
				}
				ImageDatasets imageDatasets = new ImageDatasets(readImageDatasetChannels(fileName, bMergeChannels, timeIndex, resize));
				JAXBContext jaxbContext = JAXBContext.newInstance(new Class[] {ImageDatasets.class});
				String s = createXML(imageDatasets, jaxbContext);
				System.out.println(createXmlDelimiter(false)+s+createXmlDelimiter(true));
			}else if(args[0].toLowerCase().equals(BioformatsImageDatasetReader.IMAGEDATAMULTI/*"imagedatamulti"*/)) {
				double timeInterval = Double.parseDouble(args[args.length-1]);
				boolean isTimeSeries = Boolean.parseBoolean(args[args.length-2]);
				File[] files = new File[args.length-3];
				for (int i = 0; i < files.length; i++) {
					files[i] = new File(args[i+1]);
				}
				ImageDataset imageDataset = readImageDatasetFromMultiFiles(files, isTimeSeries, timeInterval);
				JAXBContext jaxbContext = JAXBContext.newInstance(new Class[] {ImageDataset.class});
				String s = createXML(imageDataset, jaxbContext);
				System.out.println(createXmlDelimiter(false)+s+createXmlDelimiter(true));
			}
			//File[] files, boolean isTimeSeries, double timeInterval
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace(System.err);
			System.exit(1);
		}
		
	}
	private static String createXmlDelimiter(boolean bEnd) {
		return (bEnd?BioformatsImageDatasetReader.BIOF_XML_END_DELIM:BioformatsImageDatasetReader.BIOF_XML_START_DELIM)+"\n";
	}
	private static String createXML(Object theObject,JAXBContext jaxbContext) throws Exception{
		Marshaller m = jaxbContext.createMarshaller();
		// for pretty-print XML in JAXB
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		StringWriter writer = new StringWriter();
		// Write to list to a writer
		m.marshal(theObject, writer);
		return writer.toString();
	}

	public static ImageSizeInfo getImageSizeInfo(String fileName,Integer forceZSize) throws Exception{
		ImageSizeInfo imageSizeInfo = null;
		if(fileName.toUpperCase().endsWith(".ZIP")){
			if(forceZSize != null){
				throw new RuntimeException("ZIP file unexpected forceZSize");
			}
			ImageDataset[] imageDatasets  =  readZipFile(fileName, false, false,null);
			ZipFile zipFile = null;
			try{
				zipFile = new ZipFile(new File(fileName),ZipFile.OPEN_READ);
				ISize iSize = new ISize(imageDatasets[0].getAllImages()[0].getNumX(), imageDatasets[0].getAllImages()[0].getNumY(), zipFile.size());
				double[] times = new double[] {0};
				imageSizeInfo = new ImageSizeInfo(fileName,iSize, imageDatasets.length, times, 0);				
			}finally{
				if(zipFile != null){
					try{zipFile.close();}catch(Exception e){e.printStackTrace();/*ignore and continue*/}
				}
			}
		}else{
			ImageReader imageReader = getImageReader(fileName);
			DomainInfo domainInfo = getDomainInfo(imageReader);
			ISize iSize = (forceZSize == null?domainInfo.getiSize():new ISize(domainInfo.getiSize().getX(), domainInfo.getiSize().getY(), forceZSize));
			Time[] times = getTimes(imageReader);
			double[] times_double = new double[times.length];
			for (int i=0;i<times.length;i++){
				times_double[i] = times[i].value().doubleValue();
			}
			imageSizeInfo = new ImageSizeInfo(fileName, iSize,imageReader.getSizeC(),times_double,0);
		}
		return imageSizeInfo;
	}

	private static ImageReader getImageReader(String imageID) throws FormatException,IOException{
		ImageReader imageReader = new ImageReader();
		imageReader.setNormalized(true);//normalize floats
		// create OME-XML metadata store of the latest schema version
		MetadataStore store = MetadataTools.createOMEXMLMetadata();
		// or if you want a specific schema version, you can use:
		//MetadataStore store = MetadataTools.createOMEXMLMetadata(null, "2007-06");
//		MetadataRetrieve meta = (MetadataRetrieve) store;
		store.createRoot();
		imageReader.setMetadataStore(store);
//		FormatReader.debug = true;
		imageReader.setId(imageID);
		return imageReader;
	}

	public static ImageDataset readImageDataset(String imageID) throws Exception {
		return readImageDatasetChannels(imageID,true,null,null)[0];
	}
	
	private static ImageDataset[] readZipFile(String imageID,boolean bAll,boolean bMergeChannels,ISize resize) throws Exception{
		ZipFile zipFile = new ZipFile(new File(imageID),ZipFile.OPEN_READ);
		Vector<Vector<ImageDataset>> imageDataForEachChannelV = new Vector<Vector<ImageDataset>>();
		Enumeration<? extends ZipEntry> enumZipEntry = zipFile.entries();
		int numChannels = -1;
		//Sort entryNames because ZipFile doesn't guarantee order
		TreeMap<String, Integer> sortedChannelsTreeMap = new TreeMap<String, Integer>();
		while (enumZipEntry.hasMoreElements()){

			ZipEntry entry = enumZipEntry.nextElement();
			if (entry.isDirectory()) {
				continue;
			}
			String entryName = entry.getName();
			String imageFileSuffix = null;
			int dotIndex = entryName.indexOf(".");
			if(dotIndex != -1){
				imageFileSuffix = entryName.substring(dotIndex);
			}
			InputStream zipInputStream = zipFile.getInputStream(entry);
			File tempImageFile = File.createTempFile("ImgDataSetReader", imageFileSuffix);
			tempImageFile.deleteOnExit();
			FileOutputStream fos = new FileOutputStream(tempImageFile,false);
			byte[] buffer = new byte[50000];
			while (true){
				int bytesRead = zipInputStream.read(buffer);
				if (bytesRead==-1){
					break;
				}
				fos.write(buffer, 0, bytesRead);
			}
			fos.close();
			zipInputStream.close(); 
			ImageDataset[] imageDatasetChannels = null; 
			try {
					imageDatasetChannels = readImageDatasetChannels(tempImageFile.getAbsolutePath(),bMergeChannels,null,resize);
			} catch (UnknownFormatException ufe) {
				//we check the exception, rather than testing a priori, because this is a rare use case
				if (HiddenNonImageFile.isHidden(entryName)) {
					continue;
				}
				throw ufe;
			}
			if(numChannels == -1){
				numChannels = imageDatasetChannels.length;
				for (int i = 0; i < numChannels; i++) {
					imageDataForEachChannelV.add(new Vector<ImageDataset>());
				}
			}
			if(numChannels != imageDatasetChannels.length){
				throw new RuntimeException("ZipFile reader found images with different number of channels");
			}
			
			sortedChannelsTreeMap.put(entryName, imageDataForEachChannelV.elementAt(0).size());
			for (int i = 0; i < numChannels; i++) {
				imageDataForEachChannelV.elementAt(i).add(imageDatasetChannels[i]);
			}
			
			tempImageFile.delete();
			if(!bAll){
				break;
			}
		}
		zipFile.close();
		ImageDataset[] completeImageDatasetChannels = new ImageDataset[imageDataForEachChannelV.size()];
		Integer[] sortIndexes = sortedChannelsTreeMap.values().toArray(new Integer[0]);
		for (int i = 0; i < completeImageDatasetChannels.length; i++) {
			//sort based on entryName
			ImageDataset[] unSortedChannel = imageDataForEachChannelV.elementAt(i).toArray(new ImageDataset[0]);
			ImageDataset[] sortedChannel = new ImageDataset[unSortedChannel.length];
			for (int j = 0; j < unSortedChannel.length; j++) {
				sortedChannel[j] = unSortedChannel[sortIndexes[j]];
			}
			completeImageDatasetChannels[i] = createZStack(sortedChannel);			
		}
		return completeImageDatasetChannels;

	}
	
	private static class DomainInfo{
		private ISize iSize;
		private Extent extent;
		private Origin origin;
		public DomainInfo(ISize iSize, Extent extent, Origin origin) {
			super();
			this.iSize = iSize;
			this.extent = extent;
			this.origin = origin;
		}
		public ISize getiSize() {
			return iSize;
		}
		public Extent getExtent() {
			return extent;
		}
		public Origin getOrigin(){
			return origin;
		}
	}
	
	private static DomainInfo getDomainInfo(ImageReader imageReader){
		MetadataRetrieve metadataRetrieve = (MetadataRetrieve)imageReader.getMetadataStore();
		int sizeX = metadataRetrieve.getPixelsSizeX(0).getValue();
		int sizeY = metadataRetrieve.getPixelsSizeY(0).getValue();
		int sizeZ = metadataRetrieve.getPixelsSizeZ(0).getValue();
		ISize iSize = new ISize(sizeX, sizeY, sizeZ);
		Number pixelSizeX_m = um(metadataRetrieve.getPixelsPhysicalSizeX(0));
		Number pixelSizeY_m = um(metadataRetrieve.getPixelsPhysicalSizeY(0));
		Number pixelSizeZ_m = um(metadataRetrieve.getPixelsPhysicalSizeZ(0));
		if (pixelSizeX_m==null || pixelSizeX_m.doubleValue()==0){
			pixelSizeX_m = .3e-6;
		}
		if (pixelSizeY_m==null || pixelSizeY_m.doubleValue()==0){
			pixelSizeY_m = .3e-6;
		}
		if (pixelSizeZ_m==null || pixelSizeZ_m.doubleValue()==0 || pixelSizeZ_m.doubleValue()==1){
			pixelSizeZ_m = 0.9e-6;
		}
		
		Extent extent = null;
		if (pixelSizeX_m.doubleValue()>0 && 
				pixelSizeY_m.doubleValue()>0 && 
				pixelSizeZ_m.doubleValue()>0){
			extent = new Extent(pixelSizeX_m.doubleValue()*iSize.getX(),
								pixelSizeY_m.doubleValue()*iSize.getY(),
								pixelSizeZ_m.doubleValue()*iSize.getZ());
		}else{
			extent = new Extent(1,1,1);
		}
		Origin origin = new Origin(0,0,0);
		return new DomainInfo(iSize, extent,origin);
	}

	/* (non-Javadoc)
	 * @see cbit.vcell.VirtualMicroscopy.ImageDatasetReader#readImageDatasetChannels(java.lang.String, cbit.vcell.client.task.ClientTaskStatusSupport, boolean, java.lang.Integer, org.vcell.util.ISize)
	 */
	public static ImageDataset[] readImageDatasetChannels(String imageID, boolean bMergeChannels,Integer timeIndex,ISize resize) throws Exception {
		if (imageID.toUpperCase().endsWith(".ZIP")){
			return readZipFile(imageID, true, bMergeChannels,resize);
		}

		ImageReader imageReader = getImageReader(imageID);
		if(BIO_FORMATS_DEBUG){printInfo(imageReader);}
		DomainInfo domainInfo = getDomainInfo(imageReader);
		IFormatReader formatReader = imageReader.getReader(imageID);
		
		if(formatReader instanceof ZeissLSMReader){
			//this fixes lsm .mdb problems
			formatReader.close();
			formatReader.setGroupFiles(false);
			formatReader.setId(imageID);
		}
		boolean bUnsigned = formatReader.getPixelType()==1 || formatReader.getPixelType()==3 || formatReader.getPixelType()==5;
		if(BIO_FORMATS_DEBUG){
			//BIOFormats Image API documentation
			//42 - image width (getSizeX()) 
			//43 - image height (getSizeY()) 
			//44 - number of series per file (getSeriesCount()) 
			//45 - total number of images per series (getImageCount()) 
			//46 - number of slices in the current series (getSizeZ()) 
			//47 - number of timepoints in the current series (getSizeT()) 
			//48 - number of actual channels in the current series (getSizeC()) 
			//49 - number of channels per image (getRGBChannelCount()) 
			//50 - the ordering of the images within the current series (getDimensionOrder()) 
			//51 - whether each image is RGB (isRGB()) 
			//52 - whether the pixel bytes are in little-endian order (isLittleEndian()) 
			//53 - whether the channels in an image are interleaved (isInterleaved()) 
			//54 - the type of pixel data in this file (getPixelType()) 
			System.out.println("image Info from imageReader("+
				"file="+imageID+","+
				"x="+formatReader.getSizeX()+","+
				"y="+formatReader.getSizeY()+","+
				"z="+formatReader.getSizeZ()+","+
				"c="+formatReader.getSizeC()+","+
				"effective c="+formatReader.getEffectiveSizeC()+","+//how to interpret rgbChannelCount
				"t="+formatReader.getSizeT()+","+
				"seriesCnt="+formatReader.getSeriesCount()+","+
				"imageCnt="+formatReader.getImageCount()+","+
				"isRGB="+formatReader.isRGB()+","+
				"RGBChannelCnt="+formatReader.getRGBChannelCount()+","+
				"dimOrder="+formatReader.getDimensionOrder()+","+
				"littleEndian="+formatReader.isLittleEndian()+","+
				"isInterleave="+formatReader.isInterleaved()+","+
				"pixelType="+formatReader.getPixelType()+" ("+FormatTools.getPixelTypeString(formatReader.getPixelType())+")"+
				"unsigedPixelType="+bUnsigned+")"+
				")");
		}
		try{
			int CHANNELCOUNT = Math.max(formatReader.getRGBChannelCount(),formatReader.getSizeC());
			UShortImage[][] ushortImageCTZArr = new UShortImage[(bMergeChannels?1:CHANNELCOUNT)][(timeIndex==null?formatReader.getSizeT()*formatReader.getSizeZ():formatReader.getSizeZ())];
			int tzIndex = 0;
			for (int tndx = (timeIndex==null?0:timeIndex); tndx <= (timeIndex==null?formatReader.getSizeT()-1:timeIndex); tndx++) {
				for (int zndx = 0; zndx < formatReader.getSizeZ(); zndx++) {
					BufferedImage bi = null;
					int[] mergePixels = (bMergeChannels?new int[(resize==null?domainInfo.getiSize().getX():resize.getX())*(resize==null?domainInfo.getiSize().getY():resize.getY())]:null);
					for (int cndx = 0; cndx < formatReader.getSizeC(); cndx++) {
						int index = formatReader.getIndex(zndx, cndx, tndx);
						System.err.println("z="+zndx+"c="+cndx+"t="+tndx+" indx="+index);
						byte[] imgPlaneBytes = formatReader.openBytes(index);
						ByteBuffer bb = ByteBuffer.wrap(imgPlaneBytes);
						bb.order((formatReader.isLittleEndian()?ByteOrder.LITTLE_ENDIAN:ByteOrder.BIG_ENDIAN));
						short[] shorts = new short[formatReader.getSizeX()*formatReader.getSizeY()];
						if(formatReader.getBitsPerPixel() == 8) {
							for (int i = 0; i < shorts.length; i++) {
								shorts[i] = (short)(0x00FF&imgPlaneBytes[i]);
							}
						}else if(formatReader.getBitsPerPixel() == 16) {
							for (int i = 0; i < shorts.length; i++) {
								shorts[i] = bb.getShort();
							}
						}else if(formatReader.getPixelType() == FormatTools.UINT32) {
							for (int i = 0; i < shorts.length; i++) {
								shorts[i] = (short)(bb.getInt());
							}
						}else if(formatReader.getPixelType() == FormatTools.FLOAT) {
							for (int i = 0; i < shorts.length; i++) {
								shorts[i] = (short)(bb.getFloat()*65535);
							}
						}else if(formatReader.getPixelType() == FormatTools.DOUBLE) {
							for (int i = 0; i < shorts.length; i++) {
								shorts[i] = (short)(bb.getDouble()*65535);
							}
						}else {
							throw new Exception("Expecting bitsPerPixel to be 8 or 16 but got "+formatReader.getBitsPerPixel());
						}
						bi = AWTImageTools.makeImage(shorts, formatReader.getSizeX(), formatReader.getSizeY(), false);

						if(resize != null){
							double scaleFactor = (double)resize.getX()/(double)formatReader.getSizeX();
						    AffineTransform scaleAffineTransform = AffineTransform.getScaleInstance(scaleFactor,scaleFactor);
						    AffineTransformOp scaleAffineTransformOp = new AffineTransformOp( scaleAffineTransform, (bi.getColorModel() instanceof IndexColorModel?AffineTransformOp.TYPE_NEAREST_NEIGHBOR:AffineTransformOp.TYPE_BILINEAR));
						    	BufferedImage scaledImage = new BufferedImage(resize.getX(),resize.getY(),bi.getType());
						    	bi = scaleAffineTransformOp.filter( bi, scaledImage);
						}
						if(!bMergeChannels) {
							ushortImageCTZArr[cndx][tzIndex] =
								new UShortImage(AWTImageTools.getShorts(bi)[0],domainInfo.getOrigin(),domainInfo.getExtent(),
										(resize==null?domainInfo.getiSize().getX():resize.getX()),
										(resize==null?domainInfo.getiSize().getY():resize.getY()),
										1);
						}else {
							short[] chanshorts = AWTImageTools.getShorts(bi)[0];
							for (int i = 0; i < mergePixels.length; i++) {
								mergePixels[i]+= chanshorts[i]&0x0000FFFF;
							}
						}
					}
					if(bMergeChannels) {
						for (int i = 0; i < mergePixels.length; i++) {
							mergePixels[i]/= formatReader.getSizeC();
						}
						ushortImageCTZArr[0][tzIndex] =
								new UShortImage(AWTImageTools.getShorts(bi)[0],domainInfo.getOrigin(),domainInfo.getExtent(),
									(resize==null?domainInfo.getiSize().getX():resize.getX()),
									(resize==null?domainInfo.getiSize().getY():resize.getY()),
									1);
					}
					tzIndex++;
				}
			}
			
			int numZ = Math.max(1,formatReader.getSizeZ());
			Time[] times = getTimes(imageReader);
			if(timeIndex != null){
				times = new Time[] {times[timeIndex]};
			}
			ImageDataset[] imageDataset = new ImageDataset[ushortImageCTZArr.length];
			double[] times_double = new double[times.length];
			for (int i = 0; i < times.length; i++) {
				times_double[i] = times[i].value().doubleValue();
			}
			for (int c = 0; c < imageDataset.length; c++) {
				ArrayList<UShortImage> images = new ArrayList<UShortImage>();
				for (UShortImage s : ushortImageCTZArr[c]){
					images.add(s);
				}
				imageDataset[c] = new ImageDataset(ushortImageCTZArr[c],times_double,numZ);
			}
			return imageDataset;
		}finally{
			if(formatReader != null){
				formatReader.close();
			}
		}
	}
	
	private static void printInfo(ImageReader imageReader){
		MetadataRetrieve meta = (MetadataRetrieve)imageReader.getMetadataStore();
		System.out.println("from Metadata Store("+
		meta.getPixelsSizeX(0).getValue()+","+
		meta.getPixelsSizeY(0).getValue()+","+
		meta.getPixelsSizeZ(0).getValue()+","+
		meta.getPixelsSizeC(0).getValue()+","+
		meta.getPixelsSizeT(0).getValue()+")");

		Integer ii = new Integer(0);			
		System.out.println("creationDate: "+meta.getImageAcquisitionDate(ii));
		System.out.println("description: "+meta.getImageDescription(ii));
		System.out.println("dimension order: "+meta.getPixelsDimensionOrder(ii));
		System.out.println("image name: "+meta.getImageName(ii));
		System.out.println("pixel type: "+meta.getPixelsType(ii));
		try{
			System.out.println("stage name: "+meta.getStageLabelName(ii));
			System.out.println("stage X: "+meta.getStageLabelX(ii));
			System.out.println("stage Y: "+meta.getStageLabelY(ii));
			System.out.println("stage Z: "+meta.getStageLabelZ(ii));
		}catch(Exception e){e.printStackTrace();}
		System.out.println("big endian: "+meta.getPixelsBinDataBigEndian(ii, ii));
		System.out.println("pixel size X: "+meta.getPixelsPhysicalSizeX(ii));
		System.out.println("pixel size Y: "+meta.getPixelsPhysicalSizeY(ii));
		System.out.println("pixel size Z: "+meta.getPixelsPhysicalSizeZ(ii));
		if (meta.getPixelsPhysicalSizeX(0)!=null){
		System.out.println("   image Size X: "+(meta.getPixelsSizeX(ii).getValue()*um(meta.getPixelsPhysicalSizeX(0)).doubleValue())+" microns");
		}
		if (meta.getPixelsPhysicalSizeY(0)!=null){
		System.out.println("   image Size Y: "+(meta.getPixelsSizeY(ii).getValue()*um(meta.getPixelsPhysicalSizeY(0)).doubleValue())+" microns");
		}
		System.out.println("size X: "+meta.getPixelsSizeX(ii).getValue());
		System.out.println("size Y: "+meta.getPixelsSizeY(ii).getValue());
		System.out.println("size Z: "+meta.getPixelsSizeZ(ii).getValue());
		System.out.println("size C: "+meta.getPixelsSizeC(ii).getValue());
		System.out.println("size T: "+meta.getPixelsSizeT(ii).getValue());

		for (int i=0; i<imageReader.getSeriesCount(); i++) {
			imageReader.setSeries(i);
		
		System.out.println("image size from imageReader("+
				imageReader.getSizeX()+","+
				imageReader.getSizeY()+","+
				imageReader.getSizeZ()+","+
				imageReader.getSizeC()+","+
				imageReader.getSizeT()+")");
		System.out.println("image size from Metadata Store("+
				meta.getPixelsSizeX(i).getValue()+","+
				meta.getPixelsSizeY(i).getValue()+","+
				meta.getPixelsSizeZ(i).getValue()+","+
				meta.getPixelsSizeC(i).getValue()+","+
				meta.getPixelsSizeT(i).getValue()+")");
		
		}

	}
	
	
//	  /** Outputs timing details per timepoint. */
//	  public static void printTimingPerTimepoint(IMetadata meta, int series) {
//	    System.out.println();
//	    System.out.println(
//	      "Timing information per timepoint (from beginning of experiment):");
//	    int planeCount = meta.getPlaneCount(series);
//	    for (int i = 0; i < planeCount; i++) {
//	      Double deltaT = meta.getPlaneDeltaT(series, i);
//	      if (deltaT == null) continue;
//	      // convert plane ZCT coordinates into image plane index
//	      int z = meta.getPlaneTheZ(series, i).getValue().intValue();
//	      int c = meta.getPlaneTheC(series, i).getValue().intValue();
//	      int t = meta.getPlaneTheT(series, i).getValue().intValue();
//	      if (z == 0 && c == 0) {
//	        System.out.println("\tTimepoint #" + t + " = " + deltaT + " s");
//	      }
//	    }
//	  }

	private static Time[] getTimes(ImageReader imageReader){
		MetadataRetrieve meta = (MetadataRetrieve)imageReader.getMetadataStore();
		Time[] timeFArr = new Time[imageReader.getSizeT()];
		int planeCount = meta.getPlaneCount(0);
		Unit<Time> unit_time = ome.units.UNITS.SECOND;
		//Read raw times
		for (int i = 0; i < planeCount; i++) {
			Time deltaT = null;
			Time planeDeltaT = meta.getPlaneDeltaT(0, i);
		      if (planeDeltaT == null){
		    	  deltaT = new Time(0, unit_time);
		      }else{
		    	  deltaT = new Time(planeDeltaT.value(unit_time), unit_time);
		      }

////			Float timeF = meta.getPlaneTimingDeltaT(0, 0, i);
//			// convert plane ZCT coordinates into image plane index
			int z = meta.getPlaneTheZ(0, i).getValue().intValue();//0;//(meta.getPlaneTheZ(0, 0, i) == null?0:meta.getPlaneTheZ(0, 0, i).intValue());
			int c = meta.getPlaneTheC(0, i).getValue().intValue();//0;//(meta.getPlaneTheC(0, 0, i) == null?0:meta.getPlaneTheC(0, 0, i).intValue());
			int t = meta.getPlaneTheT(0, i).getValue().intValue();//i;//(meta.getPlaneTheT(0, 0, i) == null?0:meta.getPlaneTheT(0, 0, i).intValue());
			if (z == 0 && c == 0) {
				timeFArr[t] = deltaT;
			}

//			int index = imageReader.getIndex(z, c, t);
//			Double timeF = meta.getPlaneDeltaT(0, i);
//			timeFArr[t] = timeF;
////			System.out.println("times[" + index + "] = " + timeFArr[index]);
		}
		//Subtract time zero
		Time[] times = new Time[timeFArr.length];
		for (int i = 0; i < times.length; i++) {
		   if(timeFArr[i] == null){
			   if((i==times.length-1) && (times.length > 2)){
				   timeFArr[i] = new Time(timeFArr[i-1].value().doubleValue() + timeFArr[i-1].value().doubleValue() - timeFArr[i-2].value().doubleValue(),unit_time);
			   }else{
				   times = null;
				   break;
			   }
		   }
		   times[i] = new Time(timeFArr[i].value().doubleValue()-timeFArr[0].value().doubleValue(),unit_time);
		}
		//If can't read properly then fill in with integers
		if(times == null){
			times = new Time[imageReader.getSizeT()];
			for (int i = 0; i < times.length; i++) {
				times[i] = new Time(i,unit_time);
			}
		}
		return times;
	}

	public static ImageDataset readImageDatasetFromMultiFiles(File[] files, boolean isTimeSeries, double timeInterval) throws Exception 
	{
		int numImages = files.length;
		UShortImage[] images = new UShortImage[numImages];
		
		
		// Added Feb, 2008. The varaibles added below are used for calculating the time used.
		//we want to update the loading progress every 2 seconds.
		long start = System.currentTimeMillis();
		long end;
		//Added Feb, 2008. Calculate the progress only when loading data to Virtual Microscopy
		int imageCount = 0;
		for (int i = 0; i < numImages; i++) 
		{		
			ImageReader imageReader = new ImageReader();
			MetadataStore store = MetadataTools.createOMEXMLMetadata();
			MetadataRetrieve meta = (MetadataRetrieve) store;
		    store.createRoot();
		    imageReader.setMetadataStore(store);
//		    FormatReader.debug = true;
		    String imageID = files[i].getAbsolutePath();
		    imageReader.setId(imageID);
			IFormatReader formatReader = imageReader.getReader(imageID);
			formatReader.setId(imageID);
			
			try{
				BufferedImage origBufferedImage = BufferedImageReader.makeBufferedImageReader(formatReader).openImage(0);//only one image each loop
				short[][] pixels = AWTImageTools.getShorts(origBufferedImage);
				int minValue = ((int)pixels[0][0])&0xffff;
				int maxValue = ((int)pixels[0][0])&0xffff;
				for (int j = 0; j < pixels[0].length; j++) {
					minValue = Math.min(minValue,0xffff&((int)pixels[0][i]));
					maxValue = Math.max(maxValue,0xffff&((int)pixels[0][i]));
				}
				Length physSizeX = meta.getPixelsPhysicalSizeX(0);
				Length physSizeY = meta.getPixelsPhysicalSizeX(1);
				Length physSizeZ = meta.getPixelsPhysicalSizeX(2);
				Float pixelSizeX_m = (physSizeX==null?null:um(physSizeX).floatValue());
				Float pixelSizeY_m = (physSizeY==null?null:um(physSizeY).floatValue());
				Float pixelSizeZ_m = (physSizeZ==null?null:um(physSizeZ).floatValue());
				if (pixelSizeX_m==null || pixelSizeX_m==0f){
					pixelSizeX_m = 0.3e-6f;
				}
				if (pixelSizeY_m==null || pixelSizeY_m==0f){
					pixelSizeY_m = 0.3e-6f;
				}
				if (pixelSizeZ_m==null || pixelSizeZ_m==0f || pixelSizeZ_m==1f){
					pixelSizeZ_m = 0.9e-6f;
				}
				int sizeX = meta.getPixelsSizeX(0).getValue();
				int sizeY = meta.getPixelsSizeY(0).getValue();
				int sizeZ = meta.getPixelsSizeZ(0).getValue();
				
				if (sizeZ > 1){
//					throw new RuntimeException("3D images not yet supported");
				}
				Extent extent = null;
				if (pixelSizeX_m!=null && pixelSizeY_m!=null && pixelSizeZ_m!=null && pixelSizeX_m>0 && pixelSizeY_m>0 && pixelSizeZ_m>0){
//					extent = new Extent(pixelSizeX_m*sizeX*1e6,pixelSizeY_m*sizeY*1e6,pixelSizeZ_m*sizeZ*1e6);
					extent = new Extent(pixelSizeX_m*sizeX,pixelSizeY_m*sizeY,pixelSizeZ_m*sizeZ);
				}
//				ArrayList<Short> pixelList = new ArrayList<Short>();
//				for (short s : pixels[0]){
//					pixelList.add(new Short(s));
//				}
				images[i] = new UShortImage(pixels[0],new Origin(0,0,0),extent,sizeX,sizeY,sizeZ);//new UShortImage(pixelList,new ISize(sizeX,sizeY,1),extent,new Origin(0,0,0));
				imageCount ++;
				//added Jan 2008, calculate the progress only when loading data to Virtual Microscopy
				// added Jan 2008, calculate the progress only when loading data to Virtual Microscopy
				
				for (int j=0; j<formatReader.getSeriesCount(); j++) {
					formatReader.setSeries(j);
				}

			}finally{
				if(formatReader != null){
					formatReader.close();
				}
			}// end of try
		}// end of for loop
		
		// Read in the time stamps for individual time series images from formatReader.
		double[] times = null;
		int numZ = 1;
		if(isTimeSeries)
		{
			times = new double[numImages];
			for (int i = 0; i < times.length; i++) {
				times[i] = i * timeInterval ;
			}
		}
		else
		{
			numZ = numImages;
		}
//		List<UShortImage> imageList = Arrays.asList(images);
//		ArrayList<Double> timeList = new ArrayList<Double>();
//		for (double t : times){
//			timeList.add(t);
//		}
		ImageDataset imageDataset = new ImageDataset(images,times,numZ);//new ImageDataset(imageList,timeList,numZ);
		return imageDataset;
	}
	
	private static Number um(final Length length) {
		if(length != null){
			return length.value(UNITS.MICROMETER);
		}
		return null;
	}

	private static ImageDataset createZStack(ImageDataset[] argImageDatasets) {
		// Error checking
		if (argImageDatasets.length == 0) {
			throw new RuntimeException("Cannot perform FRAP analysis on null image.");
		}
		
		int tempNumX = argImageDatasets[0].getAllImages()[0].getNumX();
		int tempNumY = argImageDatasets[0].getAllImages()[0].getNumY();
		int tempNumZ = argImageDatasets[0].getSizeZ();
		int tempNumC = 1;
		int tempNumT = argImageDatasets[0].getImageTimeStamps().length;
		if (tempNumZ!=1 || tempNumC!=1 || tempNumT!=1){
			throw new RuntimeException("each ImageDataset in z-stack must be 2D, single channel, and one time");
		}
		UShortImage[] ushortImages = new UShortImage[argImageDatasets.length];
		ushortImages[0] = argImageDatasets[0].getAllImages()[0];
		for (int i = 1; i < argImageDatasets.length; i++) {
			UShortImage img = argImageDatasets[i].getAllImages()[0];
			if (img.getNumX()!=tempNumX || img.getNumY()!=tempNumY || img.getNumZ()!=tempNumZ){
				throw new RuntimeException("ImageDataset sub-images not same dimension");
			}
			ushortImages[i] = img;
			ushortImages[i].setExtent(new Extent(img.getExtent().getX(),img.getExtent().getY(),img.getExtent().getZ()*argImageDatasets.length));
		}
		return new ImageDataset(ushortImages,argImageDatasets[0].getImageTimeStamps(),ushortImages.length);
	}
	

}
