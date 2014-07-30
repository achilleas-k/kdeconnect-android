package org.kde.kdeconnect.UserInterface;

import android.annotation.TargetApi;
import android.app.ListActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;

public class CustomDevicesActivity extends ListActivity {

    public static final String KEY_CUSTOM_DEVLIST_PREFERENCE  = "device_list_preference";
    private static final String IP_DELIM = "::";

    String[] iplist = new String[] {"10.8.0.13", "10.8.0.100"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //initializeDeviceList(this);

        setContentView(R.layout.custom_ip_list);
        //ArrayAdapter adapter = new ArrayAdapter<String>(this,
        //                R.layout.custom_ip_list, iplist);
        setListAdapter(new ArrayAdapter(this, android.R.layout.simple_list_item_1, iplist));

        //ListView listView = (ListView) findViewById(R.id.ip_addr_list);

        //listView.setAdapter(adapter);

    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Do something when a list item is clicked
        Log.i("CustomDevicesActivity", "Item clicked");
    }

    private void initPreferences(final ListPreference ipListPref) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        ipListPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newCustomIp) {
                if (newCustomIp.toString().isEmpty()) {
                    return false;
                } else {
                    Log.i("CustomDevicesActivity", "New IP address: " + newCustomIp);
                    ipListPref.setSummary(getString(
                            R.string.custom_device_list_summary,
                            newCustomIp.toString()));

                    //Broadcast the device information again since it has changed
                    BackgroundService.RunCommand(CustomDevicesActivity.this, new BackgroundService.InstanceCallback() {
                        @Override
                        public void onServiceStart(BackgroundService service) {
                            service.onNetworkChange();
                        }
                    });
                    return true;
                }
            }
        });
        ipListPref.setSummary(getString(
                R.string.custom_device_list_summary,
                sharedPreferences.getString(KEY_CUSTOM_DEVLIST_PREFERENCE, "")));
    }

    String serializeIpList(ArrayList<String> iplist) {
        String serialized = "";
        for (String ipaddr : iplist) {
            serialized += IP_DELIM +ipaddr;
        }
        return serialized;
    }

    ArrayList<String> deserializeIpList(String serialized) {
        ArrayList<String> iplist = new ArrayList<String>();
        for (String ipaddr : serialized.split(IP_DELIM)) {
            iplist.add(ipaddr);
        }
        return iplist;
    }

    public static void initializeDeviceList(Context context){
        String deviceList = PreferenceManager.getDefaultSharedPreferences(context).getString(
                KEY_CUSTOM_DEVLIST_PREFERENCE,
                "");
        if(deviceList.isEmpty()){
            Log.i("CustomDevicesActivity", "Initialising empty custom device list");
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
                    KEY_CUSTOM_DEVLIST_PREFERENCE,
                    deviceList).commit();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class CustomDevicesFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.general_preferences);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            if (getActivity() != null) {
                ((CustomDevicesActivity)getActivity()).initPreferences(
                        (ListPreference) findPreference(KEY_CUSTOM_DEVLIST_PREFERENCE));
            }
        }
    }
}
