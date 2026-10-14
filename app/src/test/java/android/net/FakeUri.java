package android.net;

import java.util.Collections;
import java.util.List;

public class FakeUri extends Uri {
    private final String uriString;

    public FakeUri(String uriString) {
        this.uriString = uriString;
    }

    @Override public boolean isHierarchical() { return false; }
    @Override public boolean isRelative() { return false; }
    @Override public String toString() { return uriString; }
    @Override public String getScheme() { return "content"; }
    @Override public String getSchemeSpecificPart() { return uriString; }
    @Override public String getEncodedSchemeSpecificPart() { return uriString; }
    @Override public String getAuthority() { return "media"; }
    @Override public String getEncodedAuthority() { return "media"; }
    @Override public String getUserInfo() { return null; }
    @Override public String getEncodedUserInfo() { return null; }
    @Override public String getHost() { return "media"; }
    @Override public int getPort() { return -1; }
    @Override public String getPath() { return "/" + uriString; }
    @Override public String getEncodedPath() { return "/" + uriString; }
    @Override public String getQuery() { return null; }
    @Override public String getEncodedQuery() { return null; }
    @Override public String getFragment() { return null; }
    @Override public String getEncodedFragment() { return null; }
    @Override public List<String> getPathSegments() { return Collections.singletonList(uriString); }
    @Override public String getLastPathSegment() { return uriString; }
    @Override public Builder buildUpon() { return null; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {}
    @Override public int compareTo(Uri o) { return uriString.compareTo(o.toString()); }
    @Override public boolean equals(Object o) { return o instanceof Uri && uriString.equals(o.toString()); }
    @Override public int hashCode() { return uriString.hashCode(); }
}
