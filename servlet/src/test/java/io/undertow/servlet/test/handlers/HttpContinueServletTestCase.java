/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.servlet.test.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.servlet.Servlets;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class HttpContinueServletTestCase {

    private static volatile boolean accept = false;

    @BeforeClass
    public static void setup() {
        DeploymentUtils.setupServlet(Servlets.servlet(ContinueConsumeServlet.class).addMappings("/path"),
                Servlets.servlet(ContinueIgnoreServlet.class).addMappings("/ignore"));
    }

    @Before
    public void before() {
        Assume.assumeFalse(DefaultServer.isAjp());
    }

    @Test
    public void testHttpContinueRejected() throws IOException {
        accept = false;
        String message = "My HTTP Request!";
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter("http.protocol.wait-for-continue", Integer.MAX_VALUE);

        TestHttpClient client = new TestHttpClient();
        client.setParams(httpParams);
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/path");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message));

            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.EXPECTATION_FAILED, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testHttpContinueAccepted() throws IOException {
        accept = true;
        String message = "My HTTP Request!";
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter("http.protocol.wait-for-continue", Integer.MAX_VALUE);

        TestHttpClient client = new TestHttpClient();
        client.setParams(httpParams);
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/path");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message));

            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(message, HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testHttpContinueIgnored() throws IOException {
        accept = true;
        String message = "My HTTP Request!";
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter("http.protocol.wait-for-continue", Integer.MAX_VALUE);

        TestHttpClient client = new TestHttpClient();
        client.setParams(httpParams);
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/ignore");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message));

            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("", HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
    //UNDERTOW-162
    @Test
    public void testHttpContinueAcceptedWithChunkedRequest() throws IOException {
        accept = true;
        String message = "My HTTP Request!";
        HttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter("http.protocol.wait-for-continue", Integer.MAX_VALUE);

        TestHttpClient client = new TestHttpClient();
        client.setParams(httpParams);
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/path");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message) {
                @Override
                public long getContentLength() {
                    return -1;
                }
            });

            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(message, HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    public static class ContinueConsumeServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            try {
                if (!accept) {
                    resp.setStatus(StatusCodes.EXPECTATION_FAILED);
                    return;
                }
                byte[] buffer = new byte[1024];
                final ByteArrayOutputStream b = new ByteArrayOutputStream();
                int r = 0;
                final OutputStream outputStream = resp.getOutputStream();
                final InputStream inputStream = req.getInputStream();
                while ((r = inputStream.read(buffer)) > 0) {
                    b.write(buffer, 0, r);
                }
                outputStream.write(b.toByteArray());
                outputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class ContinueIgnoreServlet extends HttpServlet {

        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        }
    }
}
