package autosaveworld.zlibs.com.dropbox.core;

import static autosaveworld.zlibs.com.dropbox.core.util.StringUtil.jq;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import autosaveworld.zlibs.com.dropbox.core.http.HttpRequestor;
import autosaveworld.zlibs.com.dropbox.core.json.JsonArrayReader;
import autosaveworld.zlibs.com.dropbox.core.json.JsonReadException;
import autosaveworld.zlibs.com.dropbox.core.json.JsonReader;
import autosaveworld.zlibs.com.dropbox.core.util.CountingOutputStream;
import autosaveworld.zlibs.com.dropbox.core.util.IOUtil;
import autosaveworld.zlibs.com.dropbox.core.util.LangUtil;
import autosaveworld.zlibs.com.fasterxml.jackson.core.JsonLocation;
import autosaveworld.zlibs.com.fasterxml.jackson.core.JsonParser;
import autosaveworld.zlibs.com.fasterxml.jackson.core.JsonToken;

/**
 * Use this class to make remote calls to the Dropbox API. You'll need an access token first, normally acquired via {@link DbxWebAuth}.
 *
 * <p>
 * This class has no mutable state, so it's thread safe as long as you pass in a thread safe {@link HttpRequestor} implementation.
 * </p>
 */
public final class DbxClient {

	private final DbxRequestConfig requestConfig;
	private final String accessToken;
	private final DbxHost host;

	/**
	 * @param accessToken
	 *            The OAuth 2 access token (that you got from Dropbox) that gives your app the ability to make Dropbox API calls against some particular user's account. The standard way to get one of these is to use {@link DbxWebAuth} to send your user through Dropbox's OAuth 2 authorization flow.
	 */
	public DbxClient(DbxRequestConfig requestConfig, String accessToken) {
		this(requestConfig, accessToken, DbxHost.Default);
	}

	/**
	 * The same as {@link #DbxClient(DbxRequestConfig, String)} except you can also set the hostnames of the Dropbox API servers. This is used in testing. You don't normally need to call this.
	 */
	public DbxClient(DbxRequestConfig requestConfig, String accessToken, DbxHost host) {
		if (requestConfig == null) {
			throw new IllegalArgumentException("'requestConfig' is null");
		}
		if (accessToken == null) {
			throw new IllegalArgumentException("'accessToken' is null");
		}
		if (host == null) {
			throw new IllegalArgumentException("'host' is null");
		}

		this.requestConfig = requestConfig;
		this.accessToken = accessToken;
		this.host = host;
	}

	/**
	 * Returns the {@code DbxRequestConfig} that was passed in to the constructor.
	 */
	public DbxRequestConfig getRequestConfig() {
		return requestConfig;
	}

	/**
	 * Returns the {@code DbxAccessToken} that was passed in to the constructor.
	 */
	public String getAccessToken() {
		return accessToken;
	}

	// -----------------------------------------------------------------
	// /metadata

	/**
	 * Get the file or folder metadata for a given path.
	 *
	 * <pre>
	 * DbxClient dbxClient = ...
	 * DbxEntry entry = dbxClient.getMetadata("/Photos");
	 * if (entry == null) {
	 *     System.out.println("No file or folder at that path.");
	 * } else {
	 *     System.out.print(entry.toStringMultiline());
	 * }
	 * </pre>
	 *
	 * @param path
	 *            The path to the file or folder (see {@link DbxPath}).
	 *
	 * @return If there is a file or folder at the given path, return the metadata for that path. If there is no file or folder there, return {@code null}.
	 */
	public DbxEntry getMetadata(final String path) throws DbxException {
		DbxPath.checkArg("path", path);

		String host = this.host.api;
		String apiPath = "1/metadata/auto" + path;
		String[] params = { "list", "false", };

		return doGet(host, apiPath, params, null, new DbxRequestUtil.ResponseHandler<DbxEntry>() {
			@Override
			public DbxEntry handle(HttpRequestor.Response response) throws DbxException {
				if (response.statusCode == 404) {
					return null;
				}
				if (response.statusCode != 200) {
					throw DbxRequestUtil.unexpectedStatus(response);
				}
				// Will return 'null' for "is_deleted=true" entries.
				return DbxRequestUtil.readJsonFromResponse(DbxEntry.Reader, response.body);
			}
		});
	}

	/**
	 * Get the metadata for a given path; if the path refers to a folder, get all the children's metadata as well.
	 *
	 * <pre>
	 * DbxClient dbxClient = ...
	 * DbxEntry entry = dbxClient.getMetadata("/Photos");
	 * if (entry == null) {
	 *     System.out.println("No file or folder at that path.");
	 * } else {
	 *     System.out.print(entry.toStringMultiline());
	 * }
	 * </pre>
	 *
	 * @param path
	 *            The path (starting with "/") to the file or folder (see {@link DbxPath}).
	 *
	 * @return If there is no file or folder at the given path, return {@code null}. Otherwise, return the metadata for that path and the metadata for all its immediate children (if it's a folder).
	 */
	public DbxEntry.WithChildren getMetadataWithChildren(String path) throws DbxException {
		return getMetadataWithChildrenBase(path, DbxEntry.WithChildren.Reader);
	}

	private <T> T getMetadataWithChildrenBase(String path, final JsonReader<? extends T> reader) throws DbxException {
		DbxPath.checkArg("path", path);

		String host = this.host.api;
		String apiPath = "1/metadata/auto" + path;

		String[] params = { "list", "true", "file_limit", "25000", };

		return doGet(host, apiPath, params, null, new DbxRequestUtil.ResponseHandler<T>() {
			@Override
			public T handle(HttpRequestor.Response response) throws DbxException {
				if (response.statusCode == 404) {
					return null;
				}
				if (response.statusCode != 200) {
					throw DbxRequestUtil.unexpectedStatus(response);
				}
				// Will return 'null' for "is_deleted=true" entries.
				return DbxRequestUtil.readJsonFromResponse(reader, response.body);
			}
		});
	}

	// --------------------------------------------------------
	// File uploads -- automatically choose between /files_put and
	// /chunked_upload

	/**
	 * A wrapper around {@link #uploadFile(String, DbxWriteMode, long, DbxStreamWriter)} that lets you pass in an {@link InputStream}. The entire stream {@code contents} will be uploaded.
	 *
	 * <pre>
	 * DbxClient dbxClient = ...
	 * File f = new File("ReadMe.txt")
	 * dbxClient.uploadFile("/ReadMe.txt", {@link DbxWriteMode#add()}, f.length(), new FileInputStream(f))
	 * </pre>
	 *
	 * @param targetPath
	 *            The path to the file on Dropbox (see {@link DbxPath}). If a file at that path already exists on Dropbox, then the {@code writeMode} parameter will determine what happens.
	 *
	 * @param writeMode
	 *            Determines what to do if there's already a file at the given {@code targetPath}.
	 *
	 * @param numBytes
	 *            The number of bytes in the given stream. Use {@code -1} if you don't know.
	 *
	 * @param contents
	 *            The source of file contents. This stream will be automatically closed (whether or not the upload succeeds).
	 *
	 * @throws IOException
	 *             If there's an error reading from {@code in}.
	 */
	public DbxEntry.File uploadFile(String targetPath, DbxWriteMode writeMode, long numBytes, InputStream contents) throws DbxException, IOException {
		return uploadFile(targetPath, writeMode, numBytes, new DbxStreamWriter.InputStreamCopier(contents));
	}

	/**
	 * Upload file contents to Dropbox, getting contents from the given {@link DbxStreamWriter}.
	 *
	 * <pre>
	 * DbxClient dbxClient = ...
	 * <b>// Create a file on Dropbox with 100 3-digit random numbers, one per line.</b>
	 * final int numRandoms = 100;
	 * int fileSize = numRandoms * 4;  <b>3 digits, plus a newline</b>
	 * dbxClient.uploadFile("/Randoms.txt", {@link DbxWriteMode#add()}, fileSize,
	 *     new DbxStreamWriter&lt;RuntimeException&gt;() {
	 *         public void write(OutputStream out) throws IOException
	 *         {
	 *             Random rand = new Random();
	 *             PrintWriter pw = new PrintWriter(out);
	 *             for (int i = 0; i < numRandoms; i++) {
	 *                 pw.printf("%03d\n", rand.nextInt(1000));
	 *             }
	 *             pw.flush();
	 *         }
	 *     });
	 * </pre>
	 *
	 * @param targetPath
	 *            The path to the file on Dropbox (see {@link DbxPath}). If a file at that path already exists on Dropbox, then the {@code writeMode} parameter will determine what happens.
	 *
	 * @param writeMode
	 *            Determines what to do if there's already a file at the given {@code targetPath}.
	 *
	 * @param numBytes
	 *            The number of bytes you're going to upload via the returned {@link DbxClient.Uploader}. Use {@code -1} if you don't know ahead of time.
	 *
	 * @param writer
	 *            A callback that will be called when it's time to actually write out the body of the file.
	 *
	 * @throws E
	 *             If {@code writer.write()} throws an exception, it will propagate out of this function.
	 */
	public <E extends Throwable> DbxEntry.File uploadFile(String targetPath, DbxWriteMode writeMode, long numBytes, DbxStreamWriter<E> writer) throws DbxException, E {
		Uploader uploader = startUploadFile(targetPath, writeMode, numBytes);
		return finishUploadFile(uploader, writer);
	}

	private static final long ChunkedUploadThreshold = 8 * 1024 * 1024;
	private static final int ChunkedUploadChunkSize = 4 * 1024 * 1024;

	/**
	 * Start an API request to upload a file to Dropbox. Returns a {@link DbxClient.Uploader} object that lets you actually send the file contents via {@link DbxClient.Uploader#getBody()}. When you're done copying the file body, call {@link DbxClient.Uploader#finish}.
	 *
	 * <p>
	 * You need to close the {@link Uploader} when you're done with it. Use a {@code try}/{@code finally} to make sure you close it in all cases.
	 * </p>
	 *
	 * <pre>
	 * DbxClient dbxClient = ...
	 * DbxClient.Uploader uploader = dbxClient.startUploadFile(...)
	 * DbxEntry.File md;
	 * try {
	 *     writeMyData(uploader.body);
	 *     md = uploader.finish();
	 * }
	 * finally {
	 *     uploader.close();
	 * }
	 * </pre>
	 *
	 * @param targetPath
	 *            The path to the file on Dropbox (see {@link DbxPath}). If a file at that path already exists on Dropbox, then the {@code writeMode} parameter will determine what happens.
	 *
	 * @param writeMode
	 *            Determines what to do if there's already a file at the given {@code targetPath}.
	 *
	 * @param numBytes
	 *            The number of bytes you're going to upload via the returned {@link DbxClient.Uploader}. Use {@code -1} if you don't know ahead of time.
	 */
	public Uploader startUploadFile(String targetPath, DbxWriteMode writeMode, long numBytes) throws DbxException {
		if (numBytes < 0) {
			if (numBytes != -1) {
				throw new IllegalArgumentException("numBytes must be -1 or greater; given " + numBytes);
			}
			// If the number of bytes isn't given in advance, use chunked
			// uploads.
			return startUploadFileChunked(targetPath, writeMode, numBytes);
		} else if (numBytes > ChunkedUploadThreshold) {
			// If the number of bytes is more than some threshold, use chunked
			// uploads.
			return startUploadFileChunked(targetPath, writeMode, numBytes);
		} else {
			// Otherwise, use the regular /files_put upload.
			return startUploadFileSingle(targetPath, writeMode, numBytes);
		}
	}

	public <E extends Throwable> DbxEntry.File finishUploadFile(Uploader uploader, DbxStreamWriter<E> writer) throws DbxException, E {
		NoThrowOutputStream streamWrapper = new NoThrowOutputStream(uploader.getBody());
		try {
			writer.write(streamWrapper);
			return uploader.finish();
		} catch (NoThrowOutputStream.HiddenException ex) {
			// We hid our OutputStream's IOException from their writer.write()
			// function so that
			// we could properly raise a NetworkIO exception if something went
			// wrong with the
			// network stream.
			throw new DbxException.NetworkIO(ex.underlying);
		} finally {
			uploader.close();
		}
	}

	// --------------------------------------------------------
	// /files_put

	/**
	 * Similar to {@link #uploadFile}, except always uses the /files_put API call. One difference is that {@code numBytes} must not be negative.
	 */
	public Uploader startUploadFileSingle(String targetPath, DbxWriteMode writeMode, long numBytes) throws DbxException {
		DbxPath.checkArg("targetPath", targetPath);
		if (numBytes < 0) {
			throw new IllegalArgumentException("numBytes must be zero or greater");
		}

		String host = this.host.content;
		String apiPath = "1/files_put/auto" + targetPath;

		ArrayList<HttpRequestor.Header> headers = new ArrayList<HttpRequestor.Header>();
		headers.add(new HttpRequestor.Header("Content-Type", "application/octet-stream"));
		headers.add(new HttpRequestor.Header("Content-Length", Long.toString(numBytes)));

		HttpRequestor.Uploader uploader = DbxRequestUtil.startPut(requestConfig, accessToken, host, apiPath, writeMode.params, headers);

		return new SingleUploader(uploader, numBytes);
	}

	public <E extends Throwable> DbxEntry.File uploadFileSingle(String targetPath, DbxWriteMode writeMode, long numBytes, DbxStreamWriter<E> writer) throws DbxException, E {
		Uploader uploader = startUploadFileSingle(targetPath, writeMode, numBytes);
		return finishUploadFile(uploader, writer);
	}

	private static final class SingleUploader extends Uploader {
		private HttpRequestor.Uploader httpUploader;
		private final long claimedBytes;
		private final CountingOutputStream body;

		public SingleUploader(HttpRequestor.Uploader httpUploader, long claimedBytes) {
			if (claimedBytes < 0) {
				throw new IllegalArgumentException("'numBytes' must be greater than or equal to 0");
			}

			this.httpUploader = httpUploader;
			this.claimedBytes = claimedBytes;
			body = new CountingOutputStream(httpUploader.body);
		}

		@Override
		public OutputStream getBody() {
			return body;
		}

		/**
		 * Cancel the upload.
		 */
		@Override
		public void abort() {
			if (httpUploader == null) {
				throw new IllegalStateException("already called 'finish', 'abort', or 'close'");
			}
			HttpRequestor.Uploader p = httpUploader;
			httpUploader = null;

			p.abort();
		}

		/**
		 * Release the resources related to this {@code Uploader} instance. If {@code close()} or {@link #abort()} has already been called, this does nothing. If neither has been called, this is equivalent to calling {@link #abort()}.
		 */
		@Override
		public void close() {
			// If already close'd or aborted, then don't do anything.
			if (httpUploader == null) {
				return;
			}

			abort();
		}

		/**
		 * When you're done writing the file contents to {@link #body}, call this to indicate that you're done. This will actually finish the underlying HTTP request and return the uploaded file's {@link DbxEntry}.
		 */
		@Override
		public DbxEntry.File finish() throws DbxException {
			if (httpUploader == null) {
				throw new IllegalStateException("already called 'finish', 'abort', or 'close'");
			}
			HttpRequestor.Uploader u = httpUploader;
			httpUploader = null;

			HttpRequestor.Response response;
			final long bytesWritten;
			try {
				bytesWritten = body.getBytesWritten();

				// Make sure the uploaded the same number of bytes they said
				// they were going to upload.
				if (claimedBytes != bytesWritten) {
					u.abort();
					throw new IllegalStateException("You said you were going to upload " + claimedBytes + " bytes, but you wrote " + bytesWritten + " bytes to the Uploader's 'body' stream.");
				}

				response = u.finish();
			} catch (IOException ex) {
				throw new DbxException.NetworkIO(ex);
			} finally {
				u.close();
			}

			return DbxRequestUtil.finishResponse(response, new DbxRequestUtil.ResponseHandler<DbxEntry.File>() {
				@Override
				public DbxEntry.File handle(HttpRequestor.Response response) throws DbxException {
					if (response.statusCode != 200) {
						throw DbxRequestUtil.unexpectedStatus(response);
					}
					DbxEntry entry = DbxRequestUtil.readJsonFromResponse(DbxEntry.Reader, response.body);
					if (entry instanceof DbxEntry.Folder) {
						throw new DbxException.BadResponse("uploaded file, but server returned metadata entry for a folder");
					}
					DbxEntry.File f = (DbxEntry.File) entry;
					// Make sure the server agrees with us on how many
					// bytes are in the file.
					if (f.numBytes != bytesWritten) {
						throw new DbxException.BadResponse("we uploaded " + bytesWritten + ", but server returned metadata entry with file size " + f.numBytes);
					}
					return f;
				}
			});
		}
	}

	// -----------------------------------------------------------------
	// /chunked_upload, /commit_chunked_upload

	private static final class ChunkedUploadState {
		public final String uploadId;
		public final long offset;

		public ChunkedUploadState(String uploadId, long offset) {
			if (uploadId == null) {
				throw new IllegalArgumentException("'uploadId' can't be null");
			}
			if (uploadId.length() == 0) {
				throw new IllegalArgumentException("'uploadId' can't be empty");
			}
			if (offset < 0) {
				throw new IllegalArgumentException("'offset' can't be negative");
			}
			this.uploadId = uploadId;
			this.offset = offset;
		}

		public static final JsonReader<ChunkedUploadState> Reader = new JsonReader<ChunkedUploadState>() {
			@Override
			public ChunkedUploadState read(JsonParser parser) throws IOException, JsonReadException {
				JsonLocation top = JsonReader.expectObjectStart(parser);

				String uploadId = null;
				long bytesComplete = -1;

				while (parser.getCurrentToken() == JsonToken.FIELD_NAME) {
					String fieldName = parser.getCurrentName();
					parser.nextToken();

					try {
						if (fieldName.equals("upload_id")) {
							uploadId = JsonReader.StringReader.readField(parser, fieldName, uploadId);
						} else if (fieldName.equals("offset")) {
							bytesComplete = JsonReader.readUnsignedLongField(parser, fieldName, bytesComplete);
						} else {
							JsonReader.skipValue(parser);
						}
					} catch (JsonReadException ex) {
						throw ex.addFieldContext(fieldName);
					}
				}

				JsonReader.expectObjectEnd(parser);

				if (uploadId == null) {
					throw new JsonReadException("missing field \"upload_id\"", top);
				}
				if (bytesComplete == -1) {
					throw new JsonReadException("missing field \"offset\"", top);
				}

				return new ChunkedUploadState(uploadId, bytesComplete);
			}
		};
	}

	/**
	 * Internal function called by both chunkedUploadFirst and chunkedUploadAppend.
	 */
	private <E extends Throwable> HttpRequestor.Response chunkedUploadCommon(String[] params, long chunkSize, DbxStreamWriter<E> writer) throws DbxException, E {
		String apiPath = "1/chunked_upload";

		ArrayList<HttpRequestor.Header> headers = new ArrayList<HttpRequestor.Header>();
		headers.add(new HttpRequestor.Header("Content-Type", "application/octet-stream"));
		headers.add(new HttpRequestor.Header("Content-Length", Long.toString(chunkSize)));

		HttpRequestor.Uploader uploader = DbxRequestUtil.startPut(requestConfig, accessToken, host.content, apiPath, params, headers);
		try {
			try {
				NoThrowOutputStream nt = new NoThrowOutputStream(uploader.body);
				writer.write(nt);
				long bytesWritten = nt.getBytesWritten();
				if (bytesWritten != chunkSize) {
					throw new IllegalStateException("'chunkSize' is " + chunkSize + ", but 'writer' only wrote " + bytesWritten + " bytes");
				}
				return uploader.finish();
			} catch (IOException ex) {
				throw new DbxException.NetworkIO(ex);
			} catch (NoThrowOutputStream.HiddenException ex) {
				throw new DbxException.NetworkIO(ex.underlying);
			}
		} finally {
			uploader.close();
		}
	}

	private ChunkedUploadState chunkedUploadCheckForOffsetCorrection(HttpRequestor.Response response) throws DbxException {
		if (response.statusCode != 400) {
			return null;
		}

		byte[] data = DbxRequestUtil.loadErrorBody(response);

		try {
			return ChunkedUploadState.Reader.readFully(data);
		} catch (JsonReadException ex) {
			// Couldn't parse out an offset correction message. Treat it like a
			// regular HTTP 400 then.
			throw new DbxException.BadRequest(DbxRequestUtil.parseErrorBody(400, data));
		}
	}

	private ChunkedUploadState chunkedUploadParse200(HttpRequestor.Response response) throws DbxException.BadResponse, DbxException.NetworkIO {
		assert response.statusCode == 200 : response.statusCode;
		return DbxRequestUtil.readJsonFromResponse(ChunkedUploadState.Reader, response.body);
	}

	/**
	 * Equivalent to {@link #chunkedUploadFirst(byte[], int, int) chunkedUploadFirst(data, 0, data.length)}.
	 */
	public String chunkedUploadFirst(byte[] data) throws DbxException {
		return chunkedUploadFirst(data, 0, data.length);
	}

	/**
	 * Upload the first chunk of a multi-chunk upload.
	 *
	 * @param data
	 *            The data to append.
	 * @param dataOffset
	 *            The start offset in {@code data} to read from.
	 * @param dataLength
	 *            The number of bytes to read from {@code data}, starting from {@code dataOffset}.
	 *
	 * @return The ID designated by the Dropbox server to identify the chunked upload.
	 *
	 * @throws DbxException
	 */
	public String chunkedUploadFirst(byte[] data, int dataOffset, int dataLength) throws DbxException {
		return chunkedUploadFirst(dataLength, new DbxStreamWriter.ByteArrayCopier(data, dataOffset, dataLength));
	}

	/**
	 * Upload the first chunk of a multi-chunk upload.
	 *
	 * @param chunkSize
	 *            The number of bytes you're going to upload in this chunk.
	 *
	 * @param writer
	 *            A callback that will be called when it's time to actually write out the body of the chunk.
	 *
	 * @return The ID designated by the Dropbox server to identify the chunked upload.
	 *
	 * @throws DbxException
	 */
	public <E extends Throwable> String chunkedUploadFirst(int chunkSize, DbxStreamWriter<E> writer) throws DbxException, E {
		HttpRequestor.Response response = chunkedUploadCommon(new String[0], chunkSize, writer);
		try {
			ChunkedUploadState correctedState = chunkedUploadCheckForOffsetCorrection(response);
			if (correctedState != null) {
				throw new DbxException.BadResponse("Got offset correction response on first chunk.");
			}

			if (response.statusCode == 404) {
				throw new DbxException.BadResponse("Got a 404, but we didn't send an upload_id");
			}

			if (response.statusCode != 200) {
				throw DbxRequestUtil.unexpectedStatus(response);
			}
			ChunkedUploadState returnedState = chunkedUploadParse200(response);

			if (returnedState.offset != chunkSize) {
				throw new DbxException.BadResponse("Sent " + chunkSize + " bytes, but returned offset is " + returnedState.offset);
			}

			return returnedState.uploadId;
		} finally {
			IOUtil.closeInput(response.body);
		}
	}

	/**
	 * Equivalent to {@link #chunkedUploadAppend(String, long, byte[], int, int) chunkedUploadAppend(uploadId, uploadOffset, data, 0, data.length)}.
	 */
	public long chunkedUploadAppend(String uploadId, long uploadOffset, byte[] data) throws DbxException {
		return chunkedUploadAppend(uploadId, uploadOffset, data, 0, data.length);
	}

	/**
	 * Append data to a chunked upload session.
	 *
	 * @param uploadId
	 *            The identifier returned by {@link #chunkedUploadFirst} to identify the chunked upload session.
	 *
	 * @param uploadOffset
	 *            The current number of bytes uploaded to the chunked upload session. The server checks this value to make sure it is correct. If it is correct, the contents of {@code data} is appended and -1 is returned. If it is incorrect, the correct offset is returned.
	 *
	 * @param data
	 *            The data to append.
	 *
	 * @param dataOffset
	 *            The start offset in {@code data} to read from.
	 *
	 * @param dataLength
	 *            The number of bytes to read from {@code data}, starting from {@code dataOffset}.
	 *
	 * @return If everything goes correctly, returns {@code -1}. If the given {@code offset} didn't match the actual number of bytes in the chunked upload session, returns the correct number of bytes.
	 *
	 * @throws DbxException
	 */
	public long chunkedUploadAppend(String uploadId, long uploadOffset, byte[] data, int dataOffset, int dataLength) throws DbxException {
		return chunkedUploadAppend(uploadId, uploadOffset, dataLength, new DbxStreamWriter.ByteArrayCopier(data, dataOffset, dataLength));
	}

	/**
	 * Append a chunk of data to a chunked upload session.
	 *
	 * @param uploadId
	 *            The identifier returned by {@link #chunkedUploadFirst} to identify the chunked upload session.
	 *
	 * @param uploadOffset
	 *            The current number of bytes uploaded to the chunked upload session. The server checks this value to make sure it is correct. If it is correct, the contents of {@code data} is appended and -1 is returned. If it is incorrect, the correct offset is returned.
	 *
	 * @param chunkSize
	 *            The size of the chunk.
	 *
	 * @param writer
	 *            A callback that will be called when it's time to actually write out the body of the chunk.
	 *
	 * @return If everything goes correctly, returns {@code -1}. If the given {@code offset} didn't match the actual number of bytes in the chunked upload session, returns the correct number of bytes.
	 *
	 * @throws DbxException
	 */
	public <E extends Throwable> long chunkedUploadAppend(String uploadId, long uploadOffset, long chunkSize, DbxStreamWriter<E> writer) throws DbxException, E {
		if (uploadId == null) {
			throw new IllegalArgumentException("'uploadId' can't be null");
		}
		if (uploadId.length() == 0) {
			throw new IllegalArgumentException("'uploadId' can't be empty");
		}
		if (uploadOffset < 0) {
			throw new IllegalArgumentException("'offset' can't be negative");
		}

		String[] params = { "upload_id", uploadId, "offset", Long.toString(uploadOffset), };
		HttpRequestor.Response response = chunkedUploadCommon(params, chunkSize, writer);
		try {
			ChunkedUploadState correctedState = chunkedUploadCheckForOffsetCorrection(response);
			if (correctedState != null) {
				if (!correctedState.uploadId.equals(uploadId)) {
					throw new DbxException.BadResponse("uploadId mismatch: us=" + jq(uploadId) + ", server=" + jq(correctedState.uploadId));
				}

				if (correctedState.offset == uploadOffset) {
					throw new DbxException.BadResponse("Corrected offset is same as given: " + uploadOffset);
				}

				return correctedState.offset;
			}

			if (response.statusCode != 200) {
				throw DbxRequestUtil.unexpectedStatus(response);
			}
			ChunkedUploadState returnedState = chunkedUploadParse200(response);

			long expectedOffset = uploadOffset + chunkSize;
			if (returnedState.offset != expectedOffset) {
				throw new DbxException.BadResponse("Expected offset " + expectedOffset + " bytes, but returned offset is " + returnedState.offset);
			}

			return -1;
		} finally {
			IOUtil.closeInput(response.body);
		}
	}

	/**
	 * Creates a file in the user's Dropbox at the given path, with file data previously uploaded via {@link #chunkedUploadFirst} and {@link #chunkedUploadAppend}.
	 *
	 * @param targetPath
	 *            The path to the file on Dropbox (see {@link DbxPath}). If a file at that path already exists on Dropbox, then the {@code writeMode} parameter will determine what happens.
	 *
	 * @param writeMode
	 *            Determines what to do if there's already a file at the given {@code targetPath}.
	 *
	 * @param uploadId
	 *            The identifier returned by {@link #chunkedUploadFirst} to identify the uploaded data.
	 */
	public DbxEntry.File chunkedUploadFinish(String targetPath, DbxWriteMode writeMode, String uploadId) throws DbxException {
		DbxPath.checkArgNonRoot("targetPath", targetPath);

		String apiPath = "1/commit_chunked_upload/auto" + targetPath;

		String[] params = { "upload_id", uploadId, };
		params = LangUtil.arrayConcat(params, writeMode.params);

		return doPost(host.content, apiPath, params, null, new DbxRequestUtil.ResponseHandler<DbxEntry.File>() {
			@Override
			public DbxEntry.File handle(HttpRequestor.Response response) throws DbxException {
				if (response.statusCode != 200) {
					throw DbxRequestUtil.unexpectedStatus(response);
				}

				DbxEntry entry = DbxRequestUtil.readJsonFromResponse(DbxEntry.Reader, response.body);
				if (entry instanceof DbxEntry.Folder) {
					throw new DbxException.BadResponse("uploaded file, but server returned metadata entry for a folder");
				}
				return (DbxEntry.File) entry;
			}
		});
	}

	/**
	 * Similar to {@link #startUploadFile}, except always uses the chunked upload API.
	 */
	public Uploader startUploadFileChunked(String targetPath, DbxWriteMode writeMode, long numBytes) {
		return startUploadFileChunked(ChunkedUploadChunkSize, targetPath, writeMode, numBytes);
	}

	/**
	 * Similar to {@link #startUploadFile}, except always uses the chunked upload API.
	 */
	public Uploader startUploadFileChunked(int chunkSize, String targetPath, DbxWriteMode writeMode, long numBytes) {
		DbxPath.checkArg("targetPath", targetPath);
		if (writeMode == null) {
			throw new IllegalArgumentException("'writeMode' can't be null");
		}

		return new ChunkedUploader(targetPath, writeMode, numBytes, new ChunkedUploadOutputStream(chunkSize));
	}

	/**
	 * Similar to {@link #uploadFile}, except always uses the chunked upload API.
	 */
	public <E extends Throwable> DbxEntry.File uploadFileChunked(String targetPath, DbxWriteMode writeMode, long numBytes, DbxStreamWriter<E> writer) throws DbxException, E {
		Uploader uploader = startUploadFileChunked(targetPath, writeMode, numBytes);
		return finishUploadFile(uploader, writer);
	}

	/**
	 * Similar to {@link #uploadFile}, except always uses the chunked upload API.
	 */
	public <E extends Throwable> DbxEntry.File uploadFileChunked(int chunkSize, String targetPath, DbxWriteMode writeMode, long numBytes, DbxStreamWriter<E> writer) throws DbxException, E {
		Uploader uploader = startUploadFileChunked(chunkSize, targetPath, writeMode, numBytes);
		return finishUploadFile(uploader, writer);
	}

	private final class ChunkedUploader extends Uploader {
		private final String targetPath;
		private final DbxWriteMode writeMode;
		private final long numBytes;
		private final ChunkedUploadOutputStream body;

		private ChunkedUploader(String targetPath, DbxWriteMode writeMode, long numBytes, ChunkedUploadOutputStream body) {
			this.targetPath = targetPath;
			this.writeMode = writeMode;
			this.numBytes = numBytes;
			this.body = body;
		}

		@Override
		public OutputStream getBody() {
			return body;
		}

		@Override
		public void abort() {
		}

		@Override
		public DbxEntry.File finish() throws DbxException {
			if (body.uploadId == null) {
				// They didn't write enough data to fill up a chunk. Use the
				// regular file upload
				// call to create the file with a single call.
				return uploadFileSingle(targetPath, writeMode, body.chunkPos, new DbxStreamWriter.ByteArrayCopier(body.chunk, 0, body.chunkPos));
			} else {
				body.finishChunk();

				// Upload whatever is left in the current chunk.
				if (numBytes != -1) {
					// Make sure the number of bytes they sent matches what they
					// said they'd send.
					if (numBytes != body.uploadOffset) {
						throw new IllegalStateException("'numBytes' is " + numBytes + " but you wrote " + body.uploadOffset + " bytes");
					}
				}
				return DbxRequestUtil.runAndRetry(3, new DbxRequestUtil.RequestMaker<DbxEntry.File, RuntimeException>() {
					@Override
					public DbxEntry.File run() throws DbxException {
						return chunkedUploadFinish(targetPath, writeMode, body.uploadId);
					}
				});
			}
		}

		@Override
		public void close() {
		}
	}

	private final class ChunkedUploadOutputStream extends OutputStream {
		private final byte[] chunk;
		private int chunkPos = 0;
		private String uploadId;
		private long uploadOffset;

		private ChunkedUploadOutputStream(int chunkSize) {
			chunk = new byte[chunkSize];
			chunkPos = 0;
		}

		@Override
		public void write(int i) throws IOException {
			chunk[chunkPos++] = (byte) i;
			try {
				finishChunkIfNecessary();
			} catch (DbxException ex) {
				throw new IODbxException(ex);
			}
		}

		private void finishChunkIfNecessary() throws DbxException {
			assert chunkPos <= chunk.length;
			if (chunkPos == chunk.length) {
				finishChunk();
			}
		}

		private void finishChunk() throws DbxException {
			if (chunkPos == 0) {
				return;
			}

			if (uploadId == null) {
				uploadId = DbxRequestUtil.runAndRetry(3, new DbxRequestUtil.RequestMaker<String, RuntimeException>() {
					@Override
					public String run() throws DbxException {
						return chunkedUploadFirst(chunk, 0, chunkPos);
					}
				});
				uploadOffset = chunkPos;
			} else {
				int arrayOffset = 0;
				while (true) {
					final int arrayOffsetFinal = arrayOffset;
					long correctedOffset = DbxRequestUtil.runAndRetry(3, new DbxRequestUtil.RequestMaker<Long, RuntimeException>() {
						@Override
						public Long run() throws DbxException {
							return chunkedUploadAppend(uploadId, uploadOffset, chunk, arrayOffsetFinal, chunkPos - arrayOffsetFinal);
						}
					});
					long expectedOffset = uploadOffset + chunkPos;
					if (correctedOffset == -1) {
						// Everything went ok.
						uploadOffset = expectedOffset;
						break;
					} else {
						// Make sure the returned offset is within what we
						// expect.
						assert correctedOffset != expectedOffset;
						if (correctedOffset < uploadOffset) {
							// Somehow the server lost track of the previous
							// data we sent it.
							throw new DbxException.BadResponse("we were at offset " + uploadOffset + ", server said " + correctedOffset);
						} else if (correctedOffset > expectedOffset) {
							// Somehow the server has more data than we gave it!
							throw new DbxException.BadResponse("we were at offset " + uploadOffset + ", server said " + correctedOffset);
						}
						// Server needs us to resend partial data.
						int adjustAmount = (int) (correctedOffset - uploadOffset);
						arrayOffset += adjustAmount;
					}
				}
			}
			chunkPos = 0;
		}

		@Override
		public void write(byte[] bytes, int offset, int length) throws IOException {
			int inputEnd = offset + length;
			int inputPos = offset;
			while (inputPos < inputEnd) {
				int spaceInChunk = chunk.length - chunkPos;
				int leftToWrite = inputEnd - inputPos;
				int bytesToCopy = Math.min(leftToWrite, spaceInChunk);
				System.arraycopy(bytes, inputPos, chunk, chunkPos, bytesToCopy);
				chunkPos += bytesToCopy;
				inputPos += bytesToCopy;
				try {
					finishChunkIfNecessary();
				} catch (DbxException ex) {
					throw new IODbxException(ex);
				}
			}
		}

		@Override
		public void close() throws IOException {
		}
	}

	/**
	 * A DbxException wrapped inside an IOException. This is necessary because sometimes we present an interface (such as OutputStream.write) that is only declared to throw {@code IOException}, but we need to throw {@code DbxException}.
	 */
	public static final class IODbxException extends IOException {
		private static final long serialVersionUID = 1L;
		public final DbxException underlying;

		public IODbxException(DbxException underlying) {
			super(underlying);
			this.underlying = underlying;
		}
	}

	/**
	 * Returns metadata for all files and folders whose name matches the query string.
	 *
	 * @param basePath
	 *            The path to search under (recursively). Pass in {@code "/"} to search everything.
	 * @param query
	 *            A space-separated list of substrings to search for. A file matches only if it contains all the substrings.
	 * @return The list of metadata entries that match the search query.
	 */
	public List<DbxEntry> searchFileAndFolderNames(String basePath, String query) throws DbxException {
		DbxPath.checkArg("basePath", basePath);
		if (query == null) {
			throw new IllegalArgumentException("'query' can't be null");
		}
		if (query.length() == 0) {
			throw new IllegalArgumentException("'query' can't be empty");
		}

		String apiPath = "1/search/auto" + basePath;
		String[] params = { "query", query };

		return doPost(host.api, apiPath, params, null, new DbxRequestUtil.ResponseHandler<List<DbxEntry>>() {
			@Override
			public List<DbxEntry> handle(HttpRequestor.Response response) throws DbxException {
				if (response.statusCode != 200) {
					throw DbxRequestUtil.unexpectedStatus(response);
				}
				return DbxRequestUtil.readJsonFromResponse(JsonArrayReader.mk(DbxEntry.Reader), response.body);
			}
		});
	}

	/**
	 * Create a new folder in Dropbox.
	 */
	public DbxEntry.Folder createFolder(String path) throws DbxException {
		DbxPath.checkArgNonRoot("path", path);

		String[] params = { "root", "auto", "path", path, };

		return doPost(host.api, "1/fileops/create_folder", params, null, new DbxRequestUtil.ResponseHandler<DbxEntry.Folder>() {
			@Override
			public DbxEntry.Folder handle(HttpRequestor.Response response) throws DbxException {
				if (response.statusCode == 403) {
					return null;
				}
				if (response.statusCode != 200) {
					throw DbxRequestUtil.unexpectedStatus(response);
				}
				DbxEntry.Folder e = DbxRequestUtil.readJsonFromResponse(DbxEntry.Folder.Reader, response.body);
				if (e == null) {
					throw new DbxException.BadResponse("got deleted folder entry");
				}
				return e;
			}
		});
	}

	/**
	 * Delete a file or folder from Dropbox.
	 */
	public void delete(String path) throws DbxException {
		DbxPath.checkArgNonRoot("path", path);

		String[] params = { "root", "auto", "path", path, };

		doPost(host.api, "1/fileops/delete", params, null, new DbxRequestUtil.ResponseHandler<Void>() {
			@Override
			public Void handle(HttpRequestor.Response response) throws DbxException {
				if (response.statusCode != 200) {
					throw DbxRequestUtil.unexpectedStatus(response);
				}
				return null;
			}
		});
	}

	// --------------------------------------------------------

	// Convenience function that calls RequestUtil.doGet with the first two
	// parameters filled in.
	private <T> T doGet(String host, String path, String[] params, ArrayList<HttpRequestor.Header> headers, DbxRequestUtil.ResponseHandler<T> handler) throws DbxException {
		return DbxRequestUtil.doGet(requestConfig, accessToken, host, path, params, headers, handler);
	}

	// Convenience function that calls RequestUtil.doPost with the first two
	// parameters filled in.
	public <T> T doPost(String host, String path, String[] params, ArrayList<HttpRequestor.Header> headers, DbxRequestUtil.ResponseHandler<T> handler) throws DbxException {
		return DbxRequestUtil.doPost(requestConfig, accessToken, host, path, params, headers, handler);
	}

	/**
	 * For uploading file content to Dropbox. Write stuff to the {@link #getBody} stream.
	 *
	 * <p>
	 * Don't call {@code close()} directly on the {@link #getBody}. Instead call either call either {@link #finish} or {@link #close} to make sure the stream and other resources are released. A safe idiom is to use the object within a {@code try} block and put a call to {@link #close()} in the {@code finally} block.
	 * </p>
	 *
	 * <pre>
	 * DbxClient.Uploader uploader = ...
	 * try {
	 *     uploader.body.write("Hello, world!".getBytes("UTF-8"));
	 *     uploader.finish();
	 * }
	 * finally {
	 *     uploader.close();
	 * }
	 * </pre>
	 */
	public static abstract class Uploader {
		public abstract OutputStream getBody();

		/**
		 * Cancel the upload.
		 */
		public abstract void abort();

		/**
		 * Release the resources related to this {@code Uploader} instance. If {@code close()} or {@link #abort()} has already been called, this does nothing. If neither has been called, this is equivalent to calling {@link #abort()}.
		 */
		public abstract void close();

		/**
		 * When you're done writing the file contents to {@link #getBody}, call this to indicate that you're done. This will actually finish the underlying HTTP request and return the uploaded file's {@link DbxEntry}.
		 */
		public abstract DbxEntry.File finish() throws DbxException;
	}

}
