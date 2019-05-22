package auxiliary;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

public class TestProxy {

    public static void main(String[] args) {

        Properties p = System.getProperties();
        Enumeration keys = p.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = (String) p.get(key);
            System.out.println(key + ": " + value);
        }

        try {
            System.setProperty("java.net.useSystemProxies", "true");
            List<Proxy> l = ProxySelector.getDefault().select(new URI("http://www.bloomberg.com/"));

            for (Proxy proxy : l) {
                System.out.println("proxy hostname : " + proxy.type());
                System.out.println(" proxy to string " + proxy.toString());
                //proxy.
                InetSocketAddress addr = (InetSocketAddress) proxy.address();

                if (addr == null) {
                    System.out.println("No Proxy");
                } else {
                    System.out.println("proxy hostname : " + addr.getHostName());
                    System.out.println("proxy port : " + addr.getPort());
                    System.out.println(" address :" + addr.getAddress());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        ////////////////////////////
//        try {    
//            System.setProperty("java.net.useSystemProxies","true");
//
//            // Use proxy vole to find the default proxy
//            ProxySearch ps = ProxySearch.getDefaultProxySearch();
//            ps.setPacCacheSettings(32, 1000*60*5);                             
//            List l = ps.getProxySelector().select(
//                    new URI("http://www.yahoo.com/"));
//
//            //... Now just do what the original did ...
//            for (Iterator iter = l.iterator(); iter.hasNext(); ) {
//                Proxy proxy = (Proxy) iter.next();
//
//                System.out.println("proxy hostname : " + proxy.type());
//                InetSocketAddress addr = (InetSocketAddress)
//                    proxy.address();
//
//                if(addr == null) {    
//                    System.out.println("No Proxy");    
//                } else {
//                    System.out.println("proxy hostname : " + 
//                            addr.getHostName());
//
//                    System.out.println("proxy port : " + 
//                            addr.getPort());    
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }        
//        
//        
//        try {
//            System.setProperty("java.net.useSystemProxies","true");
//            List<Proxy> l = ProxySelector.getDefault().select(
//                        new URI("http://www.google.com/"));
//
//            for (Iterator<Proxy> iter = l.iterator(); iter.hasNext(); ) {
//
//                Proxy proxy = iter.next();
//
//                System.out.println("proxy hostname : " + proxy.type());
//
//                InetSocketAddress addr = (InetSocketAddress)proxy.address();
//
//                if(addr == null) {
//
//                    System.out.println("No Proxy");
//
//                } else {
//                    System.out.println("proxy hostname : " + addr.getHostName());
//                    System.out.println("proxy port : " + addr.getPort());
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
}
