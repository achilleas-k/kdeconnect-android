package org.kde.kdeconnect.UserInterface;

import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;

public class CustomDevicesActivity extends ListActivity {

    private static final String LOG_ID = "CustomDevicesActivity";
    public static final String KEY_CUSTOM_DEVLIST_PREFERENCE  = "device_list_preference";
    private static final String IP_DELIM = "::";

    ArrayList<String> ipAddressList = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeDeviceList(this);
        setContentView(R.layout.custom_ip_list);
        setListAdapter(new ArrayAdapter(this, android.R.layout.simple_list_item_1, ipAddressList));


        EditText ipEntryBox = (EditText)findViewById(R.id.ip_edittext);
        ipEntryBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    addNewIp(v);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Log.i(LOG_ID, "Item clicked pos: "+position+" id: "+id);
        // remove touched item after confirmation
        // TODO: add confirmation
        ipAddressList.remove(position);
        Log.i(LOG_ID, "Removed item pos: "+position+" id: "+id);
        saveList();
        ((ArrayAdapter)getListAdapter()).notifyDataSetChanged();
    }

    public void addNewIp(View v) {
        EditText ipEntryBox = (EditText)findViewById(R.id.ip_edittext);
        String enteredText = ipEntryBox.getText().toString().trim();
        // TODO: validate IP address
        if (!enteredText.equals("")) {
            // don't add empty string (after trimming)
            ipAddressList.add(enteredText);
        }
        // TODO: allow specifying port as well

        saveList();
        // clear entry box
        ipEntryBox.setText("");
        InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);

        inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);
    }

    void saveList() {
        // add entry to list and save to preferences (unless empty)
        String serialized = "";
        if (!ipAddressList.isEmpty()) {
            serialized = serializeIpList(ipAddressList);
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(
                KEY_CUSTOM_DEVLIST_PREFERENCE, serialized).commit();
        ((ArrayAdapter)getListAdapter()).notifyDataSetChanged();

    }

    /*
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
    */

    static String serializeIpList(ArrayList<String> iplist) {
        String serialized = "";
        for (String ipaddr : iplist) {
            serialized += IP_DELIM+ipaddr;
        }
        // remove first delimiter
        serialized = serialized.substring(IP_DELIM.length());
        Log.d(LOG_ID, serialized);
        return serialized;
    }

    public static ArrayList<String> deserializeIpList(String serialized) {
        ArrayList<String> iplist = new ArrayList<String>();
        Log.d(LOG_ID, serialized);
        for (String ipaddr : serialized.split(IP_DELIM)) {
            iplist.add(ipaddr);
            Log.d(LOG_ID, ipaddr);
        }
        return iplist;
    }

    void initializeDeviceList(Context context){
        String deviceListPrefs = PreferenceManager.getDefaultSharedPreferences(context).getString(
                KEY_CUSTOM_DEVLIST_PREFERENCE,
                "");
        if(deviceListPrefs.isEmpty()){
            Log.i(LOG_ID, "Initialising empty custom device list");
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
                    KEY_CUSTOM_DEVLIST_PREFERENCE,
                    deviceListPrefs).commit();
        } else {
            Log.i(LOG_ID, "Populating device list");
            ipAddressList = deserializeIpList(deviceListPrefs);
        }
    }

    /*
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
    */
}
