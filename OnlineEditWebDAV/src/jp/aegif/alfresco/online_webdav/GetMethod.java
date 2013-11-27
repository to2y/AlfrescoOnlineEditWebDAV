/*
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package jp.aegif.alfresco.online_webdav;

import java.io.IOException;
import java.io.Writer;
import java.net.SocketException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletResponse;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.filestore.FileContentReader;
import org.alfresco.repo.web.util.HttpRangeProcessor;
import org.alfresco.repo.webdav.WebDAV;
import org.alfresco.repo.webdav.WebDAVServerException;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.Path;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.cmr.repository.datatype.TypeConverter;
import org.springframework.extensions.surf.util.I18NUtil;

/**
 * Implements the WebDAV GET method
 * 
 * @author gavinc
 */
public class GetMethod extends WebDAVMethod
{
	// Request parameters

	private static final String RANGE_HEADER_UNIT_SPECIFIER = "bytes=";
	private static final int MAX_RECURSE_ERROR_STACK = 20;
	private ArrayList<String> ifMatchTags = null;
	private ArrayList<String> ifNoneMatchTags = null;
	private Date m_ifModifiedSince = null;
	private Date m_ifUnModifiedSince = null;

	protected boolean m_returnContent = true;
	private String byteRanges;

	/**
	 * Default constructor
	 */
	public GetMethod()
	{
	}

	/**
	 * Parse the request headers
	 * 
	 * @exception WebDAVServerException
	 */
	protected void parseRequestHeaders() throws WebDAVServerException
	{
		// If the range header is present output a warning, add support later

		String strRange = m_request.getHeader(WebDAV.HEADER_RANGE);

		if (strRange != null && strRange.length() > 0)
		{
			byteRanges = strRange;
			if (logger.isDebugEnabled())
			{
				logger.debug("Range header supplied: " + byteRanges);
			}
		}

		// Capture all the If headers, process later

		String strIfMatch = m_request.getHeader(WebDAV.HEADER_IF_MATCH);

		if (strIfMatch != null && strIfMatch.length() > 0)
		{
			ifMatchTags = parseETags(strIfMatch);
		}

		String strIfNoneMatch = m_request.getHeader(WebDAV.HEADER_IF_NONE_MATCH);
		if (strIfNoneMatch != null && strIfNoneMatch.length() > 0)
		{
			ifNoneMatchTags = parseETags(strIfNoneMatch);
		}

		// Parse the dates

		SimpleDateFormat dateFormat = new SimpleDateFormat(WebDAV.HEADER_IF_DATE_FORMAT);
		String strIfModifiedSince = m_request.getHeader(WebDAV.HEADER_IF_MODIFIED_SINCE);

		if (strIfModifiedSince != null && strIfModifiedSince.length() > 0)
		{
			try
			{
				m_ifModifiedSince = dateFormat.parse(strIfModifiedSince);
			}
			catch (ParseException e)
			{
				logger.warn("Failed to parse If-Modified-Since date of " + strIfModifiedSince);
			}
		}

		String strIfUnModifiedSince = m_request.getHeader(WebDAV.HEADER_IF_UNMODIFIED_SINCE);
		if (strIfUnModifiedSince != null && strIfUnModifiedSince.length() > 0)
		{
			try
			{
				m_ifUnModifiedSince = dateFormat.parse(strIfUnModifiedSince);
			}
			catch (ParseException e)
			{
				logger.warn("Failed to parse If-Unmodified-Since date of " + strIfUnModifiedSince);
			}
		}
	}

	/**
	 * Parse the request body
	 * 
	 */
	protected void parseRequestBody() throws WebDAVServerException
	{
		// Nothing to do in this method
	}

	/**
	 * @return          Returns <tt>true</tt> always
	 */
	@Override
	protected boolean isReadOnly()
	{
		return true;
	}

	/**
	 * Exceute the WebDAV request
	 * 
	 * @exception WebDAVServerException
	 */
	protected void executeImpl() throws WebDAVServerException, Exception
	{
		FileFolderService fileFolderService = getFileFolderService();

		if (!m_returnContent)
		{
			// There are multiple cases where no content is sent (due to a HEAD request).
			// All of them require that the content length is set appropriately.
			m_response.setContentLength(0);
		}

		FileInfo realNodeInfo;
		try {
			realNodeInfo = getDAVHelper().getFileInfoFromRequestPath(m_request);
		}
		catch (FileNotFoundException e)
		{
			throw new WebDAVServerException(HttpServletResponse.SC_NOT_FOUND);
		}		

		// Return the node details, and content if requested, check that the node passes the pre-conditions

		checkPreConditions(realNodeInfo);

		// Build the response header
		m_response.setHeader(WebDAV.HEADER_ETAG, getDAVHelper().makeQuotedETag(realNodeInfo));

		Date modifiedDate = realNodeInfo.getModifiedDate();
		if (modifiedDate != null)
		{
			long modDate = DefaultTypeConverter.INSTANCE.longValue(modifiedDate);
			m_response.setHeader(WebDAV.HEADER_LAST_MODIFIED, WebDAV.formatHeaderDate(modDate));
		}

		ContentReader reader = fileFolderService.getReader(realNodeInfo.getNodeRef());
		// ensure that we generate something, even if the content is missing
		reader = FileContentReader.getSafeContentReader(
				(ContentReader) reader,
				I18NUtil.getMessage(FileContentReader.MSG_MISSING_CONTENT),
				realNodeInfo.getNodeRef(), reader);

		readContent(realNodeInfo, reader);
	}

	protected void readContent(FileInfo realNodeInfo, ContentReader reader) throws IOException,
	WebDAVServerException
	{
		try
		{
			attemptReadContent(realNodeInfo, reader);                
		}
		catch (ContentIOException e)
		{
			boolean logAsError = true;
			Throwable t = e;
			// MNT-8989: Traverse the exception cause hierarchy, if we find a SocketException at fault,
			// assume this is a dropped connection and do not log a stack trace.
			int levels = 0;
			while (t.getCause() != null)
			{
				if (t == t.getCause() || ++levels == MAX_RECURSE_ERROR_STACK)
				{
					// Avoid infinite loops.
					break;
				}
				t = t.getCause();
				if (t instanceof SocketException)
				{
					logAsError = false;
				}
			}

			if (logAsError && logger.isErrorEnabled())
			{
				// Only log at ERROR level when not a SocketException as underlying cause.
				logger.error("Error while reading content", e);
			}
			else if (logger.isDebugEnabled())
			{
				// Log other errors at DEBUG level.
				logger.debug("Error while reading content", e);                
			}

			// Note no cause parameter supplied - avoid logging stack trace elsewhere.
			throw new WebDAVServerException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	protected void attemptReadContent(FileInfo realNodeInfo, ContentReader reader)
	throws IOException
	{
		if (byteRanges != null && byteRanges.startsWith(RANGE_HEADER_UNIT_SPECIFIER))
		{
			HttpRangeProcessor rangeProcessor = new HttpRangeProcessor(getContentService());
			String userAgent = m_request.getHeader(WebDAV.HEADER_USER_AGENT);

			if (m_returnContent)
			{
				rangeProcessor.processRange(
						m_response,
						reader,
						byteRanges.substring(6),
						realNodeInfo.getNodeRef(),
						ContentModel.PROP_CONTENT,
						reader.getMimetype(),
						userAgent);
			}
		}
		else
		{
			if (m_returnContent)
			{
				// there is content associated with the node
				m_response.setHeader(WebDAV.HEADER_CONTENT_LENGTH, Long.toString(reader.getSize()));
				m_response.setHeader(WebDAV.HEADER_CONTENT_TYPE, reader.getMimetype());

				// copy the content to the response output stream
				reader.getContent(m_response.getOutputStream());
			}
		}
	}

	/**
	 * Checks the If header conditions
	 * 
	 * @param nodeInfo the node to check
	 * @throws WebDAVServerException if a pre-condition is not met
	 */
	private void checkPreConditions(FileInfo nodeInfo) throws WebDAVServerException
	{
		// Make an etag for the node

		String strETag = getDAVHelper().makeQuotedETag(nodeInfo);
		TypeConverter typeConv = DefaultTypeConverter.INSTANCE;

		// Check the If-Match header, don't send any content back if none of the tags in
		// the list match the etag, and the wildcard is not present

		if (ifMatchTags != null)
		{
			if (ifMatchTags.contains(WebDAV.ASTERISK) == false && ifMatchTags.contains(strETag) == false)
			{
				throw new WebDAVServerException(HttpServletResponse.SC_PRECONDITION_FAILED);
			}
		}

		// Check the If-None-Match header, don't send any content back if any of the tags
		// in the list match the etag, or the wildcard is present

		if (ifNoneMatchTags != null)
		{
			if (ifNoneMatchTags.contains(WebDAV.ASTERISK) || ifNoneMatchTags.contains(strETag))
			{
				throw new WebDAVServerException(HttpServletResponse.SC_NOT_MODIFIED);
			}
		}

		// Check the modified since list, if the If-None-Match header was not specified

		if (m_ifModifiedSince != null && ifNoneMatchTags == null)
		{
			Date lastModifiedDate = nodeInfo.getModifiedDate();

			long fileLastModified = lastModifiedDate != null ? typeConv.longValue(lastModifiedDate) : 0L;
			long modifiedSince = m_ifModifiedSince.getTime();

			if (fileLastModified != 0L && fileLastModified <= modifiedSince)
			{
				throw new WebDAVServerException(HttpServletResponse.SC_NOT_MODIFIED);
			}
		}

		// Check the un-modified since list

		if (m_ifUnModifiedSince != null)
		{
			Date lastModifiedDate = nodeInfo.getModifiedDate();

			long fileLastModified = lastModifiedDate != null ? typeConv.longValue(lastModifiedDate) : 0L;
			long unModifiedSince = m_ifUnModifiedSince.getTime();

			if (fileLastModified >= unModifiedSince)
			{
				throw new WebDAVServerException(HttpServletResponse.SC_PRECONDITION_FAILED);
			}
		}
	}

	/**
	 * Parses the given ETag header into a list of separate ETags
	 * 
	 * @param strETagHeader The header to parse
	 * @return A list of ETags
	 */
	private ArrayList<String> parseETags(String strETagHeader)
	{
		ArrayList<String> list = new ArrayList<String>();

		StringTokenizer tokenizer = new StringTokenizer(strETagHeader, WebDAV.HEADER_VALUE_SEPARATOR);
		while (tokenizer.hasMoreTokens())
		{
			list.add(tokenizer.nextToken().trim());
		}

		return list;
	}


	/**
	 * Formats the given size for display in a directory listing
	 * 
	 * @param strSize The content size
	 * @return The formatted size
	 */
	private String formatSize(String strSize)
	{
		String strFormattedSize = strSize;

		int length = strSize.length();
		if (length < 4)
		{
			strFormattedSize = strSize + ' ' + WebDAVHelper.encodeHTML(I18NUtil.getMessage("webdav.size.bytes"));
		}
		else if (length >= 4 && length < 7)
		{
			String strLeft = strSize.substring(0, length - 3);
			String strRight = strSize.substring(length - 3, length - 2);

			StringBuilder buffer = new StringBuilder(strLeft);
			if (!strRight.equals("0"))
			{
				buffer.append('.');
				buffer.append(strRight);
			}
			buffer.append(' ').append(WebDAVHelper.encodeHTML(I18NUtil.getMessage("webdav.size.kilobytes")));

			strFormattedSize = buffer.toString();
		}
		else
		{
			String strLeft = strSize.substring(0, length - 6);
			String strRight = strSize.substring(length - 6, length - 5);

			StringBuilder buffer = new StringBuilder(strLeft);
			if (!strRight.equals("0"))
			{
				buffer.append('.');
				buffer.append(strRight);
			}
			buffer.append(' ').append(WebDAVHelper.encodeHTML(I18NUtil.getMessage("webdav.size.megabytes")));

			strFormattedSize = buffer.toString();
		}

		return strFormattedSize;
	}
}
