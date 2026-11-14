package ru.proghouse.robocam;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ImportActivity extends AppCompatActivity {

    private ListView fileListView = null;
    private List<File> files = new ArrayList<File>();
    private String currentPath = null;
    private TextView fileTextView = null;
    private Activity thisActivity = null;
    private SharedPreferences.Editor editor = null;
    private int selectionPos = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);

        try {
            SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
            //File extFile = new File(System.getenv("EXTERNAL_STORAGE"));
            //File secFile = new File(System.getenv("SECONDARY_STORAGE"));


            //topPath = Environment.getDataDirectory().getPath(); //Environment.getExternalStorageDirectory().getPath();
            //topPath = System.getenv("EXTERNAL_STORAGE");
            //topPath = System.getenv("SECONDARY_STORAGE");

            if (savedInstanceState != null)
                currentPath = savedInstanceState.getString(ExtraKey.CURRENT_PATH);
            else {
                //currentPath = null;
                currentPath = settings.getString(ExtraKey.CURRENT_PATH, null);
                if (currentPath != null) {
                    File test = new File(currentPath);
                    if ((!test.exists()) || (!test.isDirectory()))
                        currentPath = null;
                }
            }

            thisActivity = this;

            fileTextView = (TextView) findViewById(R.id.fileTextView);
            updateTextView();

            fileListView = (ListView) findViewById(R.id.fileListView);
            files.addAll(getFiles(currentPath));
            ArrayAdapter<File> adapter = new ArrayAdapter<File>(this, R.layout.spinner_item, files) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    if (convertView == null) {
                        LayoutInflater inflater = getLayoutInflater();
                        convertView = inflater.inflate(R.layout.spinner_item, parent, false);
                    }
                    final File file = getItem(position);
                    String name;
                    if (file.isDirectory() && (
                            file.getPath().toLowerCase().equals(System.getenv("EXTERNAL_STORAGE") != null ? System.getenv("EXTERNAL_STORAGE").toLowerCase() : null)
                            || file.getPath().toLowerCase().equals(System.getenv("SECONDARY_STORAGE") != null ? System.getenv("SECONDARY_STORAGE").toLowerCase() : null)
                            || file.getPath().toLowerCase().equals(Environment.getExternalStorageDirectory().getPath().toLowerCase())))
                        name = file.getPath();
                    else
                        name = file.getName();
                    ((TextView) convertView.findViewById(R.id.textView1)).setText(name);
                    ((TextView) convertView.findViewById(R.id.textView2)).setVisibility(View.GONE);
                    ((ImageView) convertView.findViewById(R.id.imageView)).setVisibility(View.VISIBLE);
                    if (file.isDirectory())
                        ((ImageView) convertView.findViewById(R.id.imageView)).setImageResource(R.drawable.folder);
                    else
                        ((ImageView) convertView.findViewById(R.id.imageView)).setImageResource(R.drawable.document);
                    return convertView;
                }
            };
            fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    final ArrayAdapter<File> adapter = (ArrayAdapter<File>) fileListView.getAdapter();
                    File selectedFile = adapter.getItem(position);
                    if (selectedFile.isDirectory()) {
                        currentPath = selectedFile.getPath();
                        files.clear();
                        files.addAll(getFiles(currentPath));
                        adapter.notifyDataSetChanged();
                        fileListView.setSelectionAfterHeaderView();
                        updateTextView();
                    } else {
                        importSettings(selectedFile);
                    }
                }
            });
            fileListView.setAdapter(adapter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void importSettings(File file) {
        getPreferenceEditor().putString(ExtraKey.CURRENT_PATH, currentPath);
        apply();
        if (SettingsImporter.importSettings(this, file, true))
            finish();
    }

    private List<File> getFiles(String directoryPath) {
        File[] files = null;
        if (directoryPath == null || directoryPath.equals("")) {
            File extFile = Environment.getExternalStorageDirectory();
            if (((!extFile.exists()) || (!extFile.isDirectory()))
                    && System.getenv("EXTERNAL_STORAGE") != null)
                extFile = new File(System.getenv("EXTERNAL_STORAGE"));
            File secFile = null;
            if (System.getenv("SECONDARY_STORAGE") != null)
                secFile = new File(System.getenv("SECONDARY_STORAGE"));
            if (extFile != null && extFile.exists() && extFile.isDirectory()
                    && secFile != null && secFile.exists() && secFile.isDirectory())
                files = new File[] {extFile, secFile};
            else if (extFile != null && extFile.exists() && extFile.isDirectory())
                files = new File[] {extFile};
            else if (secFile != null && secFile.exists() && secFile.isDirectory())
                files = new File[] {secFile};
        }
        else {
            File directory = new File(directoryPath);
            files = directory.listFiles();
        }
        if (files == null)
            files = new File[]{};
        List<File> fileList = new ArrayList<File>();
        for (File file : files)
            if (file.isDirectory() || file.getName().trim().toLowerCase().endsWith(".xml"))
                fileList.add(file);
        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(File file, File file2) {
                if (file.isDirectory() && file2.isFile())
                    return -1;
                else if (file.isFile() && file2.isDirectory())
                    return 1;
                else
                    return file.getPath().compareTo(file2.getPath());
            }
        });
        return fileList;
    }

    @Override
    public void onBackPressed() {
        if (currentPath == null || currentPath.equals("")) {
            getPreferenceEditor().putString(ExtraKey.CURRENT_PATH, currentPath);
            apply();
            finish();
        }
        else {
            String oldCurrentPath = currentPath;
            if (currentPath.toLowerCase().equals(System.getenv("EXTERNAL_STORAGE") != null ? System.getenv("EXTERNAL_STORAGE").toLowerCase() : null)
                    || currentPath.toLowerCase().equals(System.getenv("SECONDARY_STORAGE") != null ? System.getenv("SECONDARY_STORAGE").toLowerCase() : null)
                    || currentPath.toLowerCase().equals(Environment.getExternalStorageDirectory().getPath().toLowerCase()))
                currentPath = null;
            else {
                File file = new File(currentPath);
                currentPath = file.getParent();
            }
            files.clear();
            files.addAll(getFiles(currentPath));
            ((ArrayAdapter<File>) fileListView.getAdapter()).notifyDataSetChanged();
            for (int i = 0; i < files.size(); i++)
                if (files.get(i).toString().equals(oldCurrentPath)) {
                    selectionPos = i;
                    fileListView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (selectionPos > 0)
                                fileListView.setSelection(selectionPos);
                            selectionPos = -1;
                        }
                    });
                    fileListView.setSelection(i);
                    break;
                }
            updateTextView();
        }
    }

    private void updateTextView() {
        if (currentPath == null || currentPath.equals(""))
            fileTextView.setText("/storage");
        else
            fileTextView.setText(currentPath);
    }

    public void onBackButtonClick(View v){
        this.onBackPressed();
    }

    public void onCancelButtonClick(View v){
        getPreferenceEditor().putString(ExtraKey.CURRENT_PATH, currentPath);
        apply();
        finish();
    }

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        outState.putString(ExtraKey.CURRENT_PATH, currentPath);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        currentPath = savedInstanceState.getString(ExtraKey.CURRENT_PATH);
    }

    private SharedPreferences.Editor getPreferenceEditor() {
        if (editor == null) {
            SharedPreferences settings = getSharedPreferences(ExtraKey.APP_PREFERENCE, Context.MODE_PRIVATE);
            editor = settings.edit();
        }
        return editor;
    }

    private void apply() {
        if (editor != null)
            if (Build.VERSION.SDK_INT >= 9)
                editor.apply();
            else
                editor.commit();
    }

}
