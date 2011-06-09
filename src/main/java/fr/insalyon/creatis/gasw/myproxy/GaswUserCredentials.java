
package fr.insalyon.creatis.gasw.myproxy;
/**
 *
 * @author tram
 */
public class GaswUserCredentials {
    private static final String DEFAULT_VO = "biomed";
    // user name of associated proxy stored on myproxy server
    private String login;
    // pass phrase of associated proxy stored on myproxy server
    private String password;
    // distinghished name (DN) of associated proxy stored on myproxy server in Globus format
    private String dn;
    // vo user is belong to.
    private String vo;
    // my-proxy server
    private MyProxyServer myProxyServer;
   
    public GaswUserCredentials(String login, String password){
        this(login, password, null, DEFAULT_VO); 
    } 
    
    public GaswUserCredentials(String login, String password, String dn){
        this(login, password, dn, DEFAULT_VO);        
    }
    
    public GaswUserCredentials(String login, String password, String dn, String vo){
        this.login = login;
        this.password = password;
        this.dn = RFC2253toGlobusFormat(dn);
        this.vo = vo;
    }
    
    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVo() {
        return vo;
    }

    public void setVo(String vo) {
        this.vo = vo;
    }

    /**
     * Get my-proxy server.
     * @return my-proxy server
     */
    public MyProxyServer getMyproxyServer() {
        if(myProxyServer == null) {
            myProxyServer = new MyProxyServer();
        }
        return myProxyServer;
    }
    
   /**
     * Set my-proxy server.
     * @param my-proxy server
     */
    public void setMyproxyServer(MyProxyServer myProxyServer) {
        this.myProxyServer = myProxyServer;
    }

    /**
     * Set my-proxy server.
     * @param serverName my-proxy server name
     * @param serverPort my-proxy service port
     */
    public void setMyproxyServer(String serverName, int serverPort) {
        this.myProxyServer = new MyProxyServer(serverName, serverPort);
    }
     
    private String RFC2253toGlobusFormat(String dn) {

        String[] attributes = dn.split(",");
        StringBuilder sslFormat = new StringBuilder();
        int index = attributes.length;
        while (--index > -1) {

            sslFormat.append("/");
            sslFormat.append(attributes[index]);
        }

        return sslFormat.toString();
    }
}
