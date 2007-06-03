package org.apache.log4j.chainsaw.osx;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.log4j.Logger;
import org.apache.log4j.chainsaw.LogUI;


/**
 * This class adds dynamic hooks into OSX version of Java so that various 
 * Mac-specific UI guidelines are adhered to.
 * 
 * This class uses reflection to build the necessary hooks so that there is no compile-time
 * dependency on a Mac SDK.
 * 
 * {@link  http://developer.apple.com/documentation/Java/index.html}
 * @author psmith
 *
 */
public class OSXIntegration {
    public static final boolean IS_OSX = System.getProperty("os.name").startsWith("Mac OS X");
    private static final Logger LOG = Logger.getLogger(OSXIntegration.class);
    private static Object applicationInstance;
    public static final void init(final LogUI logui) {
        LOG.info("OSXIntegration.init() called");
        if(!IS_OSX) {
            LOG.info("Not OSX, ignoring...");
            return;
        }
        try {
            Class applicationClazz = Class.forName("com.apple.eawt.Application");
            Class listenerClass = Class.forName("com.apple.eawt.ApplicationListener");
            applicationInstance = applicationClazz.newInstance();
            
//            now register that we want that Preferences menu
            Method enablePreferenceMethod = applicationClazz.getMethod("setEnabledPreferencesMenu", new Class[] {boolean.class});
            enablePreferenceMethod.invoke(applicationInstance, new Object[] {Boolean.TRUE});
            
            
            // no About menu for us for now.
            Method enableAboutMethod = applicationClazz.getMethod("setEnabledAboutMenu", new Class[] {boolean.class});
            enableAboutMethod.invoke(applicationInstance, new Object[] {Boolean.TRUE});
            
            // Need to create a Proxy object to represent an anonymous impl of the ApplicationListener class
            Object listenerProxy = Proxy.newProxyInstance(OSXIntegration.class.getClassLoader(), 
                        new Class[] {listenerClass}, 
                        new InvocationHandler() {

                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if("handlePreferences".equals(method.getName())){
                        LOG.info("handlePreferences(...) called");
                        logui.showApplicationPreferences();
                    }else if("handleQuit".equals(method.getName())){
                        setHandled(args[0], logui.exit()?Boolean.TRUE:Boolean.FALSE);
                        
                    }else if("handleAbout".equals(method.getName())) {
                        logui.showAboutBox();
                        setHandled(args[0], Boolean.TRUE);
                    }
//                    TODO think about File Open/Save options
                    return null;
                }

                private void setHandled(Object event, Boolean val) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
                    Method handleMethod =   event.getClass().getMethod("setHandled", new Class[] {boolean.class});
                    handleMethod.invoke(event, new Object[] {val});
                }});           
            // register the proxy object via the addApplicationListener method, cross fingers...
            Method registerListenerMethod = applicationClazz.getMethod("addApplicationListener", new Class[] {listenerClass});
            registerListenerMethod.invoke(applicationInstance, new Object[] {listenerProxy});
        } catch (Exception e) {
            LOG.error("Failed to setup OSXIntegration", e);
        }
    }
}
