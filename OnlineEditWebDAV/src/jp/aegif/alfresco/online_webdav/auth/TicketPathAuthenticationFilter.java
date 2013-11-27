package jp.aegif.alfresco.online_webdav.auth;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.alfresco.repo.web.filter.beans.DependencyInjectedFilter;
import org.alfresco.repo.webdav.auth.BaseAuthenticationFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TicketPathAuthenticationFilter extends BaseAuthenticationFilter implements DependencyInjectedFilter {

	private static Log logger = LogFactory.getLog(TicketPathAuthenticationFilter.class);

	@Override
	protected Log getLogger() {
		return logger;
	}

	public void doFilter(ServletContext context, ServletRequest req, ServletResponse resp, FilterChain chain)
	throws IOException, ServletException {

		HttpServletRequest request = (HttpServletRequest)req;


		if ( logger.isDebugEnabled()) {
			logger.debug("request path:" + request.getRequestURI());
		}

		//check Path as Ticket
		Pattern p = Pattern.compile(".*/webdav2/(.*)/(.*)\\..*");
		Matcher m = p.matcher(request.getRequestURI());
		boolean isMatch = m.matches();

		String ticket = null;
		if ( isMatch ) {
			//pattern match to request path
			//extract the parts of ticket
			ticket = m.group(1);
			if ( logger.isDebugEnabled()) {
				logger.debug("## ticket Str" + ticket);
			}
		}
		else {
			Pattern p2 = Pattern.compile(".*/webdav2/([^/]+)/?");
			Matcher m2 = p2.matcher(request.getRequestURI());
			isMatch = m2.matches();
			if ( isMatch ) {
				ticket = m2.group(1);

				if ( logger.isDebugEnabled()) {
					logger.debug("ticket:" + ticket);
				}
			}
			else {
				//no credential .. through this
				Pattern p3 = Pattern.compile(".*/webdav2/?$");
				Matcher m3 = p3.matcher(request.getRequestURI());
				isMatch = m3.matches();
				if ( isMatch ) {
					//Specifies Root
					if ( logger.isDebugEnabled()) {
						logger.debug("ticket:" + ticket);
					}
					return;
				}
				else {
					chain.doFilter(req, resp);
					return;
				}
			}
		}

		//try validate ticket & set currentUser

		try {
            authenticationService.validate(ticket);
            String userName = authenticationService.getCurrentUserName();
			if ( logger.isDebugEnabled()) {
				logger.debug("userName:" + userName);
			}  
		}
		catch(Exception aex) { 
			//return UNAUTHORIZED response to a client
			HttpServletResponse response = (HttpServletResponse)resp;

			response.setHeader("WWW-Authenticate", "BASIC realm=\"Alfresco DAV Server\"");
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

			response.flushBuffer();
			return;

		}

		chain.doFilter(req, resp);
	}

}
