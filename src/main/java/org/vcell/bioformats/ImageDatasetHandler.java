package org.vcell.bioformats;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.thrift.TException;
import org.vcell.bioformats.imagedataset.ISize;
import org.vcell.bioformats.imagedataset.ImageDataset;
import org.vcell.bioformats.imagedataset.ImageDatasetService;
import org.vcell.bioformats.imagedataset.ImageSizeInfo;
import org.vcell.bioformats.imagedataset.ThriftImageException;

import loci.formats.FormatException;

public class ImageDatasetHandler implements ImageDatasetService.Iface {
	
	private BioFormatsImageDatasetReader reader = new BioFormatsImageDatasetReader();
	
	public ImageDatasetHandler() {
		
	}

	@Override
	public ImageSizeInfo getImageSizeInfo(String fileName) throws ThriftImageException, TException {
		try {
			return reader.getImageSizeInfo(fileName, null);
		} catch (Exception e) {
			e.printStackTrace();
			throw new ThriftImageException("ImageDatasetService.getImageSizeInfo() failed: "+e.getMessage());
		}
	}

	@Override
	public ImageSizeInfo getImageSizeInfoForceZ(String fileName, int forceZSize) throws ThriftImageException, TException {
		try {
			return reader.getImageSizeInfo(fileName, forceZSize);
		} catch (Exception e) {
			e.printStackTrace();
			throw new ThriftImageException("ImageDatasetService.getImageSizeInfo() failed: "+e.getMessage());
		}
	}

	@Override
	public ImageDataset readImageDataset(String imageID) throws ThriftImageException, TException {
		try {
			return reader.readImageDataset(imageID);
		} catch (Exception e) {
			e.printStackTrace();
			throw new ThriftImageException("ImageDatasetService.readImageDataset() failed: "+e.getMessage());
		}
	}

	@Override
	public List<ImageDataset> readImageDatasetChannels(String imageID, boolean bMergeChannels, int timeIndex,
			ISize resize) throws ThriftImageException, TException {
		try {
			ImageDataset[] imageDatasetList = reader.readImageDatasetChannels(imageID, bMergeChannels, timeIndex, resize);
			return Arrays.asList(imageDatasetList);
		} catch (Exception e) {
			e.printStackTrace();
			throw new ThriftImageException("ImageDatasetService.readImageDatasetChannels() failed: "+e.getMessage());
		}
	}

	@Override
	public ImageDataset readImageDatasetFromMultiFiles(List<String> files, boolean isTimeSeries,
			double timeInterval) throws ThriftImageException, TException {
		File[] fileArray = new File[files.size()];
		for (int i=0;i<files.size();i++){
			fileArray[i] = new File(files.get(i));
		}
		try {
			return reader.readImageDatasetFromMultiFiles(fileArray, isTimeSeries, timeInterval);
		} catch (FormatException | IOException e) {
			e.printStackTrace();
			throw new ThriftImageException("ImageDatasetService.readImageDatasetFromMultiFiles() failed: "+e.getMessage());
		}
	}
	
}