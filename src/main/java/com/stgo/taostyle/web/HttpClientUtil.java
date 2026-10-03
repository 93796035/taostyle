package com.stgo.taostyle.web;


import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * HTTP utility class based on Apache HttpClient4.5
 *
 * @author RabyGao
 * @date Aug 9, 2019
 */
public class HttpClientUtil {

    public static String doGet(String url) {
        return doGet(url, null);
    }

    public static String doGet(String url, Map<String, String> headers) {
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;
        String result = "";
        try {
            // Create an instance of HttpClient with the address default configuration
            httpClient = HttpClients.createDefault();
            // Create an HttpGet remote connection instance
            HttpGet httpGet = new HttpGet(url);
            if (headers != null && headers.size() > 0) {
                for(Map.Entry<String, String> entry : headers.entrySet()){
                    httpGet.addHeader(entry.getKey(), entry.getValue());
                }
            }
            // Set configuration request parameters
            // Connection host service timeout, request timeout, data read timeout
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(5000).setConnectionRequestTimeout(5000).setSocketTimeout(10000)
                    .build();
            // Set the configuration for the HttpGet instance
            httpGet.setConfig(requestConfig);
            // Execute the GET request to get the returned object
            response = httpClient.execute(httpGet);
            // Get the returned data from the return object
            HttpEntity entity = response.getEntity();
            // Convert the result to a string using the toString method in EntityUtils
            result = org.apache.http.util.EntityUtils.toString(entity);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close the resource
            if (null != response) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != httpClient) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    public static String doPost(String url, Map<String, Object> paramMap) {
        return doPost(url, paramMap, null);
    }

    public static String doPost(String url, Map<String, Object> paramMap, Map<String, String> headers) {
        return doPost(url, paramMap, headers, "application/x-www-form-urlencoded", "UTF-8");
    }

    public static String doPost(String url, Map<String, Object> paramMap, Map<String, String> headers, String contentType, String charset) {
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse httpResponse = null;
        String result = "";
        // Create an instance of HttpClient
        httpClient = HttpClients.createDefault();
        // Create an HttpPost remote connection instance
        HttpPost httpPost = new HttpPost(url);
        // Configure the request parameter instance
        // Connection host service timeout, request timeout, data read timeout
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000).setConnectionRequestTimeout(5000).setSocketTimeout(10000)
                .build();
        // Configure the httppost instance Settings
        httpPost.setConfig(requestConfig);

        // Set the request header
        httpPost.addHeader("Content-Type", contentType);
        if (headers != null && headers.size() > 0) {
            for(Map.Entry<String, String> entry : headers.entrySet()){
                httpPost.addHeader(entry.getKey(), entry.getValue());
            }
        }

        // Encapsulates POST request parameters
        if (null != paramMap && paramMap.size() > 0) {
            List<NameValuePair> nvps = new ArrayList<NameValuePair>();
            // Map integrates the entrySet method to obtain the Entity
            Set<Map.Entry<String, Object>> entrySet = paramMap.entrySet();
            // Loop through to get the iterator
            Iterator<Map.Entry<String, Object>> iterator = entrySet.iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> mapEntry = iterator.next();
                nvps.add(new BasicNameValuePair(mapEntry.getKey(), mapEntry.getValue().toString()));
            }

            // Set the wrapped request parameters for HttpPost
            try {
                httpPost.setEntity(new UrlEncodedFormEntity(nvps, charset));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        try {
            // The HttpClient object performs the POST request and returns the response parameter object
            httpResponse = httpClient.execute(httpPost);
            // Gets the response content from the response object
            HttpEntity entity = httpResponse.getEntity();
            result = org.apache.http.util.EntityUtils.toString(entity);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close the resource
            if (null != httpResponse) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != httpClient) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    public static String doPostJSON(String url, String json) {
        return doPostJSON(url, json, null);
    }

    public static String doPostJSON(String url, String json, Map<String, String> headers) {
        CloseableHttpClient httpClient = null;
        try {
            httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(url);
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).setConnectionRequestTimeout(5000).setSocketTimeout(10000).build();
            // Set the configuration for the HttpPost instance
            httpPost.setConfig(requestConfig);

            // Set the request header
            httpPost.addHeader("Content-Type", "application/json;charset=UTF-8");
            if (headers != null && headers.size() > 0) {
                for (String key:headers.keySet()) {
                    httpPost.addHeader(key, headers.get(key));
                }
            }

            // Resolve the problem of Chinese gibberish
            StringEntity stringEntity = new StringEntity(json, "UTF-8");
            stringEntity.setContentEncoding("UTF-8");
            httpPost.setEntity(stringEntity);

            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                @Override
                public String handleResponse(final HttpResponse response)
                        throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            return httpClient.execute(httpPost, responseHandler);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (httpClient != null) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static String doPostGetLocation(String url, Map<String, Object> paramMap) {
        CloseableHttpResponse httpResponse = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).setConnectionRequestTimeout(5000).setSocketTimeout(10000).build();
        // Set the configuration for the HttpPost instance
        httpPost.setConfig(requestConfig);
        httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
        // Encapsulates POST request parameters
        if (null != paramMap && paramMap.size() > 0) {
            List<NameValuePair> nvps = new ArrayList<NameValuePair>();
            Set<Map.Entry<String, Object>> entrySet = paramMap.entrySet();
            Iterator<Map.Entry<String, Object>> iterator = entrySet.iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> mapEntry = iterator.next();
                nvps.add(new BasicNameValuePair(mapEntry.getKey(), mapEntry.getValue().toString()));
            }
            try {
                httpPost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        try {
            // The HttpClient object performs the POST request and returns the response parameter object
            httpResponse = httpClient.execute(httpPost);
            if (httpResponse.getStatusLine().getStatusCode() == 302 && httpResponse.getHeaders("Location").length > 0) {
                return httpResponse.getHeaders("Location")[0].getValue();
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close the resource
            if (null != httpResponse) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != httpClient) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

}
