package org.vcell.bioformats;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.vcell.bioformats.imagedataset.ImageDatasetService;

public class ImageDatasetServer {

	private static class ImageDatasetServerThread extends Thread {
		private final int port;
		ImageDatasetServerThread(int port) {
			super("imageDatasetServerThread");
			this.port = port;
			setDaemon(false);
		}
		
		@Override
		public void run() {
			startSimpleImageDatasetServer(
					new ImageDatasetService.Processor<ImageDatasetHandler>(new ImageDatasetHandler()),
					port);
		}
	}

	private static void startVCellVisitDataServerThread(int port) {
		Thread vcellProxyThread = new ImageDatasetServerThread(port);
		vcellProxyThread.start();
	}

	private static void startSimpleImageDatasetServer(ImageDatasetService.Processor<ImageDatasetHandler> processor, int port) {
		try {
			InetSocketAddress inetSocketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
			TServerTransport serverTransport = new TServerSocket(inetSocketAddress);
			TServer imageDatasetServer = new TSimpleServer(
					new org.apache.thrift.server.TServer.Args(serverTransport).processor(processor));

			System.out.println("Starting the ImageDataset Server thread on "+inetSocketAddress.getHostString()+":"+port);
			imageDatasetServer.serve();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		if (args.length!=1){
			System.out.println("usage: ImageDatasetServerThread port");
			System.exit(-1);
		}
		int port = Integer.parseInt(args[0]);
		System.out.println("vcell bioFormat plugin");
		ImageDatasetServer.startVCellVisitDataServerThread(port);
	}

}