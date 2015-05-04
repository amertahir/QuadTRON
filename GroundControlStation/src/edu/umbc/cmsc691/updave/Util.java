package edu.umbc.cmsc691.updave;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Util {
	public static void resetBuffer(byte[] buffer) {
		Arrays.fill(buffer, (byte)0);
	}
	
	public static int bytesToShort(byte[] bytes, int offset) {
		return ((bytes[offset + 1] & 0xff) << 8) | (bytes[offset] & 0xff);
	}
	
	public static String ungzip(byte[] bytes) throws Exception{
		InputStreamReader isr = new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(bytes)), StandardCharsets.UTF_8);
		StringWriter sw = new StringWriter();
		char[] chars = new char[1024];
		for (int len; (len = isr.read(chars)) > 0; ) {
			sw.write(chars, 0, len);
		}
		return sw.toString();
    }

	public static byte[] gzip(String s) throws Exception{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(bos);
		OutputStreamWriter osw = new OutputStreamWriter(gzip, StandardCharsets.UTF_8);
		osw.write(s);
		osw.close();
		return bos.toByteArray();
    }
	
	public static char[] decodeThermalImageData(String encoded) {
		byte[] bs = Base64.getDecoder().decode(encoded);
		CharBuffer dBuf = ByteBuffer.wrap(bs).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer();
		char[] array = new char[dBuf.remaining()];
		dBuf.get(array);
		return array;
	}
	
	public static BufferedImage generateThermalImage(String encodedImageData, String palette) {
		BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
		final int[] pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
		int gray = 0;
		float gray_max = 255;
		float hue = 0;
		
		char[] data = decodeThermalImageData(encodedImageData);
		float minVal = data[0];
		float maxVal = data[0];
		for (int i = 0; i < data.length; i++) {
			if (data[i] < minVal) minVal = data[i];
			if (data[i] > maxVal) maxVal = data[i];
		}
		float diff = maxVal - minVal;
		
		if (palette.equals("grayscale")) {
			for (int i = 0; i < data.length; i++) {
				gray = Math.round((data[i] - minVal) * gray_max / diff);
				pixels[i] = ((gray&0x0ff)<<16)|((gray&0x0ff)<<8)|(gray&0x0ff);
			}
		} else {
			for (int i = 0; i < data.length; i++) {
				hue = 1.0f - ((data[i] - minVal) / diff);
				pixels[i] = HSLColor.toRGB(hue, 1.0f, 0.5f);
			}
		}
		
		AffineTransform tx = new AffineTransform();
		tx.scale(2.5, 2.5);
		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BICUBIC);
		return op.filter(image, null);
	}
}
