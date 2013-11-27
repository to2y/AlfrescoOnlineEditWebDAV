package jp.aegif.alfresco.online_webdav;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Hashtable;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//import org.alfresco.repo.tenant.TenantService;
//import org.alfresco.repo.webdav.MTNodesCache;
import org.alfresco.repo.webdav.ExceptionHandler;
import org.alfresco.repo.webdav.WebDAV;
import org.alfresco.repo.webdav.WebDAVServerException;
import org.alfresco.repo.webdav.WebDAVServlet.WebDAVInitParameters;
import org.alfresco.service.ServiceRegistry;
//import org.alfresco.service.cmr.repository.NodeService;
//import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthenticationService;
//import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.FileFilterMode;
import org.alfresco.util.FileFilterMode.Client;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class OnlineWebDAVServlet extends HttpServlet
{

	// Logging
	private static Log logger = LogFactory.getLog(OnlineWebDAVServlet.class);

	// Constants
	public static final String WEBDAV_PREFIX = "webdav"; 
	//    private static final String INTERNAL_SERVER_ERROR = "Internal Server Error: ";

	// Init parameter names
	private static final String BEAN_INIT_PARAMS = "webdav.initParams";

	// Service registry, used by methods to find services to process requests
	private ServiceRegistry m_serviceRegistry;

	// Transaction service, each request is wrapped in a transaction
	private TransactionService m_transactionService;

	// WebDAV method handlers
	protected Hashtable<String,Class<? extends WebDAVMethod>> m_davMethods;

	// Root node
	//    private static MTNodesCache m_rootNodes;

	// WebDAV helper class
	private WebDAVHelper m_davHelper;

	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException,
	IOException {
		long startTime = 0;
		if (logger.isInfoEnabled())
		{
			startTime = System.currentTimeMillis();
		}

		FileFilterMode.setClient(Client.webdav);

		try
		{
			// Create the appropriate WebDAV method for the request and execute it
			final WebDAVMethod method = createMethod(request, response);

			if (method == null)
			{
				if ( logger.isErrorEnabled())
					logger.error("WebDAV method not implemented - " + request.getMethod());

				// Return an error status

				response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
				return;
			}
			//skip the ckeck below
			//            else if (method.getRootNodeRef() == null)
			//            {
			//                if ( logger.isErrorEnabled())
			//                    logger.error("No root node for request");
			//                
			//                // Return an error status
			//                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			//                return;
			//            }
			// Execute the WebDAV request, which must take care of its own transaction
			method.execute();
		}
		catch (Throwable e)
		{
			ExceptionHandler exHandler = new ExceptionHandler(e, request, response);
			exHandler.handle();
		}
		finally
		{
			if (logger.isInfoEnabled())
			{
				logger.info(request.getMethod() + " took " + (System.currentTimeMillis()-startTime) + "ms to execute ["+request.getRequestURI()+"]");
			}

			FileFilterMode.clearClient();
		}

	}

	private WebDAVMethod createMethod(HttpServletRequest request, HttpServletResponse response)
	{
		// Get the type of the current request

		String strHttpMethod = request.getMethod();

		if (logger.isDebugEnabled())
			logger.debug("WebDAV request " + strHttpMethod + " on path "
					+ request.getRequestURI());

		Class<? extends WebDAVMethod> methodClass = m_davMethods.get(strHttpMethod);
		WebDAVMethod method = null;

		if ( methodClass != null)
		{
			try
			{
				// Create the handler method

				method = methodClass.newInstance();
				//NodeRef rootNodeRef = m_rootNodes.getNodeForCurrentTenant();
				method.setDetails(request, response, m_davHelper, null);
			}
			catch (Exception ex)
			{
				// Debug

				if ( logger.isDebugEnabled())
					logger.debug(ex);
			}
		}

		// Return the WebDAV method handler, or null if not supported

		return method;
	}

	public void init(ServletConfig config) throws ServletException
	{
		super.init(config);

		// Get service registry        
		WebApplicationContext context = WebApplicationContextUtils.getWebApplicationContext(getServletContext());

		// If no context has been initialised, exit silently so config changes can be made
		if (context == null)
		{
			return;
		}

		// Get global configuration properties
		WebApplicationContext wc = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
		WebDAVInitParameters initParams = (WebDAVInitParameters) wc.getBean(BEAN_INIT_PARAMS);

		// Render this servlet permanently unavailable if its enablement property is not set
		if (!initParams.getEnabled())
		{
			throw new UnavailableException("WebDAV not enabled.");
		}

		// Get beans

		m_serviceRegistry = (ServiceRegistry)context.getBean(ServiceRegistry.SERVICE_REGISTRY);
		m_transactionService = m_serviceRegistry.getTransactionService();
		//TenantService tenantService = (TenantService) context.getBean("tenantService");
		//AuthenticationService authService = (AuthenticationService) context.getBean("authenticationService");
		//NodeService nodeService = (NodeService) context.getBean("NodeService");
		//SearchService searchService = (SearchService) context.getBean("SearchService");
		//NamespaceService namespaceService = (NamespaceService) context.getBean("NamespaceService");

		// comm4.2.d
		// Get the WebDAV helper
		m_davHelper = (WebDAVHelper) context.getBean("webDAVHelperOnline");

		// Initialize the root node --> Skip below
		//initializeRootNode(storeValue, rootPath, context, nodeService, searchService, namespaceService, tenantService, m_transactionService);
		m_transactionService.getUserTransaction(true);

		// Create the WebDAV methods table

		m_davMethods = new Hashtable<String, Class<? extends WebDAVMethod>>();

		m_davMethods.put(WebDAV.METHOD_PROPFIND, PropFindMethod.class);
		m_davMethods.put(WebDAV.METHOD_PROPPATCH, PropPatchMethod.class);
		//        m_davMethods.put(WebDAV.METHOD_COPY, CopyMethod.class);
		//        m_davMethods.put(WebDAV.METHOD_DELETE, DeleteMethod.class);
		m_davMethods.put(WebDAV.METHOD_GET, GetMethod.class);
		m_davMethods.put(WebDAV.METHOD_HEAD, HeadMethod.class);
		m_davMethods.put(WebDAV.METHOD_LOCK, LockMethod.class);
		//        m_davMethods.put(WebDAV.METHOD_MKCOL, MkcolMethod.class);
		//        m_davMethods.put(WebDAV.METHOD_MOVE, MoveMethod.class);
		m_davMethods.put(WebDAV.METHOD_OPTIONS, OptionsMethod.class);
		//        m_davMethods.put(WebDAV.METHOD_POST, PostMethod.class);
		m_davMethods.put(WebDAV.METHOD_PUT, PutMethod.class);
		m_davMethods.put(WebDAV.METHOD_UNLOCK, UnlockMethod.class);
	}

}
