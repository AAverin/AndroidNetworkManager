package aaverin.android.net;

public abstract class NetworkResponse {

    public abstract int getStatus();
    public abstract void obtainResponseStream();
    public abstract String getResponseBody() throws NetworkResponseProcessException;
}
