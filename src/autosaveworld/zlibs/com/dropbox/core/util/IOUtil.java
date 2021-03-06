package autosaveworld.zlibs.com.dropbox.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;

public class IOUtil {

	public static final int DefaultCopyBufferSize = 16 * 1024;

	public static void copyStreamToStream(InputStream in, OutputStream out) throws ReadException, WriteException {
		copyStreamToStream(in, out, DefaultCopyBufferSize);
	}

	public static void copyStreamToStream(InputStream in, OutputStream out, byte[] copyBuffer) throws ReadException, WriteException {
		while (true) {
			int count;
			try {
				count = in.read(copyBuffer);
			} catch (IOException ex) {
				throw new ReadException(ex);
			}

			if (count == -1) {
				break;
			}

			try {
				out.write(copyBuffer, 0, count);
			} catch (IOException ex) {
				throw new WriteException(ex);
			}
		}
	}

	public static void copyStreamToStream(InputStream in, OutputStream out, int copyBufferSize) throws ReadException, WriteException {
		copyStreamToStream(in, out, new byte[copyBufferSize]);
	}

	private static final ThreadLocal<byte[]> slurpBuffer = new ThreadLocal<byte[]>() {
		@Override
		protected byte[] initialValue() {
			return new byte[4096];
		}
	};

	public static byte[] slurp(InputStream in, int byteLimit) throws IOException {
		if (byteLimit < 0) {
			throw new RuntimeException("'byteLimit' must be non-negative: " + byteLimit);
		}

		byte[] copyBuffer = slurpBuffer.get();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		copyStreamToStream(in, baos, copyBuffer);
		return baos.toByteArray();
	}

	/**
	 * Closes the given input stream and ignores the IOException.
	 */
	public static void closeInput(InputStream in) {
		try {
			in.close();
		} catch (IOException ex) {
			// Ignore. We're done reading from it so we don't care if there are
			// input errors.
		}
	}

	/**
	 * Closes the given Reader and ignores the IOException.
	 */
	public static void closeInput(Reader in) {
		try {
			in.close();
		} catch (IOException ex) {
			// Ignore. We're done reading from it so we don't care if there are
			// input errors.
		}
	}

	public static abstract class WrappedException extends IOException {
		public static final long serialVersionUID = 0;

		public final IOException underlying;

		public WrappedException(String message, IOException underlying) {
			super(message + ": " + underlying.getMessage(), underlying);
			this.underlying = underlying;
		}

		public WrappedException(IOException underlying) {
			super(underlying);
			this.underlying = underlying;
		}

		@Override
		public String getMessage() {
			String m = underlying.getMessage();
			if (m == null) {
				return "";
			} else {
				return m;
			}
		}

		@Override
		public IOException getCause() {
			return underlying;
		}
	}

	public static final class ReadException extends WrappedException {
		public ReadException(String message, IOException underlying) {
			super(message, underlying);
		}

		public ReadException(IOException underlying) {
			super(underlying);
		}

		public static final long serialVersionUID = 0;
	}

	public static final class WriteException extends WrappedException {
		public WriteException(String message, IOException underlying) {
			super(message, underlying);
		}

		public WriteException(IOException underlying) {
			super(underlying);
		}

		public static final long serialVersionUID = 0;
	}

}
