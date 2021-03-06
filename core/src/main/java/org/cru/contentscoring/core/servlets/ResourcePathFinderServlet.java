package org.cru.contentscoring.core.servlets;

import java.io.IOException;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;

/**
 * This resource finder servlet is the primary one to find resource paths. It handles most URLs,
 * but not things like the home page and pages that don't end with .html, because these can't have selectors.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.resourceTypes=cq/Page",
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.selectors=find.path" })
public class ResourcePathFinderServlet extends SlingSafeMethodsServlet {
    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException {
        response.getWriter().write(request.getResource().getPath());
    }
}
