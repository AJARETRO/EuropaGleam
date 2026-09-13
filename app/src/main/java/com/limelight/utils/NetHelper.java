package com.limelight.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

public class NetHelper {
    public static boolean isActiveNetworkVpn(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connMgr.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities netCaps = connMgr.getNetworkCapabilities(activeNetwork);
                if (netCaps != null) {
                    return netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                            !netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                }
            }
        }
        else {
            NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                return activeNetworkInfo.getType() == ConnectivityManager.TYPE_VPN;
            }
        }

        return false;
    }

    public static boolean isCellularNetwork(Context context) {
        if (context == null) return false;
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connMgr.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities netCaps = connMgr.getNetworkCapabilities(activeNetwork);
                if (netCaps != null) {
                    return isCellular(netCaps);
                }
            }
        } else {
            NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                int type = activeNetworkInfo.getType();
                return type == ConnectivityManager.TYPE_MOBILE ||
                        type == ConnectivityManager.TYPE_MOBILE_DUN ||
                        type == ConnectivityManager.TYPE_MOBILE_HIPRI ||
                        type == ConnectivityManager.TYPE_MOBILE_MMS ||
                        type == ConnectivityManager.TYPE_MOBILE_SUPL;
            }
        }
        return false;
    }

    public static boolean isCellular(NetworkCapabilities netCaps) {
        if (netCaps == null) return false;
        return netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !netCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    public static boolean isWifiOrEthernet(Context context) {
        if (context == null) return false;
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connMgr.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities netCaps = connMgr.getNetworkCapabilities(activeNetwork);
                if (netCaps != null) {
                    return netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                }
            }
        } else {
            NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                int type = activeNetworkInfo.getType();
                return type == ConnectivityManager.TYPE_WIFI ||
                        type == ConnectivityManager.TYPE_ETHERNET;
            }
        }
        return false;
    }

    public static boolean isWifiConnected(Context context) {
        return isWifiOrEthernet(context);
    }
}

