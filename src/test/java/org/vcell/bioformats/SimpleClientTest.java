package org.vcell.bioformats;

import java.io.File;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.vcell.bioformats.imagedataset.ImageDataset;
import org.vcell.bioformats.imagedataset.ImageDatasetService;
import org.vcell.bioformats.imagedataset.ImageDatasetService.Client;
import org.vcell.bioformats.imagedataset.ThriftImageException;

public class SimpleClientTest
{
    public static void main(String[] args)
    {
    	if (args.length!=2){
    		System.out.println("usage: SimpleClientTest port imagefile");
    		System.exit(-1);
    	}
    	int port = Integer.parseInt(args[0]);
    	String imagefilestr = args[1];
    	File imagefile = new File(imagefilestr);
    	if (!imagefile.exists()) {
    		System.out.println("image file "+imagefilestr+" not found");
    		System.exit(-1);
    	}
    	
        try (TTransport transport = new TSocket("localhost", port);){
            transport.open();
            System.out.println("opened socket on port "+port);

            TProtocol protocol = new  TBinaryProtocol(transport);
            ImageDatasetService.Client client = new ImageDatasetService.Client(protocol);
            
            System.out.println("established connection");
            readImageFile(client, imagefile);
            System.out.println("done with all tests");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void readImageFile(Client client, File imagefile) throws ThriftImageException, TException {
		String imagePath = imagefile.getAbsolutePath();
		System.out.println("reading file "+imagePath);
		ImageDataset imageDataset = client.readImageDataset(imagePath);
		System.out.println("read file "+imagePath);
		System.out.println(imageDataset.getImages().get(0).getSize());
		System.out.println("done reading file "+imagePath);
	}
}
