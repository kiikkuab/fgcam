package com.wyywn.fgcam;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Objects;

public class Settings extends AppCompatActivity {

    Context context;
    JSONArray fgLibObj;
    JSONObject settingObj;
    JSONArray template;
    //String exPath = Environment.getExternalStorageDirectory().getPath();
    String dataPath;
    String fgLibFileName;
    String envFileName;
    String settingFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dataPath = Objects.requireNonNull(getExternalFilesDir("")).getAbsolutePath();
        fgLibFileName = dataPath + "/fglib.json";
        envFileName = dataPath + "/env.json";
        settingFileName = dataPath + "/setting.json";

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {

            context = this;

            Toolbar myToolbar = findViewById(R.id.myToolbar);
            setSupportActionBar(myToolbar);
            myToolbar.setTitle("Settings");
            myToolbar.setNavigationIcon(android.R.drawable.arrow_up_float);
            myToolbar.setNavigationOnClickListener(v1 -> {
                saveSetting();
                onBackPressed();
            });

            renderSettingsToPage();

            JSONArray jsonArray;
            try {
                jsonArray = new JSONArray(readFile("[]",fgLibFileName));
                fgLibObj = jsonArray;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            renderFgLibToPage();


            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    public void onBackPressed() {
        saveSetting();
        Intent resultIntent = new Intent();
        //resultIntent.putExtra("key", "value"); // 携带需要传递的数据
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
        super.onBackPressed();
    }

    public void onSettingButtonClick(View view) {
        //Intent resultIntent = new Intent();
        //resultIntent.putExtra("key", "value"); // 携带需要传递的数据
        //setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    public void onAddButtonClick(View view){
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.add_dialog, null);
        EditText editName = dialogView.findViewById(R.id.editName);
        EditText editPath = dialogView.findViewById(R.id.editPath);
        builder.setTitle("Add")
                .setView(dialogView)
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    String inputName = editName.getText().toString();
                    String inputPath = editPath.getText().toString();
                    writeFileSingle(fgLibFileName, inputName, inputPath);
                })
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .show();
    }

    public void writeFileSingle(String fileName, String name, String path){
        int localId = fgLibObj.length();
        JSONArray jArrayLocal = fgLibObj;
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", localId);
            jsonObject.put("name", name);
            jsonObject.put("path", path);
            jArrayLocal.put(jsonObject);

            String jsonString = jArrayLocal.toString();

            FileWriter writer = new FileWriter(fileName);
            writer.write(jsonString);
            writer.close();
        } catch (JSONException | IOException e) {
            throw new RuntimeException(e);
        }

        JSONArray jsonArray;
        try {
            jsonArray = new JSONArray(readFile("[]",fgLibFileName));
            fgLibObj = jsonArray;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        renderFgLibToPage();
    }

    public String readFile(String empty ,String fileName){
        try {
            File file = new File(fileName);
            String jsonString = empty;
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                StringBuilder text = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    text.append(line).append("\n");
                }
                jsonString = text.toString();
            }
            return jsonString;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    public void renderFgLibToPage() {
        try {
            ArrayList<String> objs = new ArrayList<>();

            if (fgLibObj.length() == 0){
                return;
            }

            for (int i = 0; i < fgLibObj.length(); i++) {
                JSONObject jsonObject = fgLibObj.getJSONObject(i);
                //String id = jsonObject.getString("id");
                String name = jsonObject.getString("name");
                String path = jsonObject.getString("path");

                objs.add("Name: " + name + "\nPath: " + path);
            }
            ListView listview = findViewById(R.id.listView);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, objs);
            listview.setAdapter(adapter);

            listview.setOnItemClickListener((parent, view, position, id) -> {
                // 在这里处理列表项的点击事件
                String selectedItem = (String) parent.getItemAtPosition(position);
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Delete")
                        .setMessage("Are you sure to delete \n" + selectedItem)
                        .setPositiveButton("Delete", (dialog, which) -> {
                            try {
                                JSONArray jArrayLocal = fgLibObj;
                                jArrayLocal.remove(position);
                                JSONArray jArrayOut = new JSONArray();

                                for (int j = 0; j < jArrayLocal.length(); j++) {
                                    JSONObject jsonObject1 = new JSONObject();
                                    jsonObject1.put("id",j);
                                    jsonObject1.put("name", jArrayLocal.getJSONObject(j).getString("name"));
                                    jsonObject1.put("path", jArrayLocal.getJSONObject(j).getString("path"));
                                    jArrayOut.put(jsonObject1);
                                }

                                String jsonString = jArrayOut.toString();

                                FileWriter writer = new FileWriter(fgLibFileName);
                                writer.write(jsonString);
                                writer.close();
                            } catch (IOException | JSONException e) {
                                throw new RuntimeException(e);
                            }
                            JSONArray jsonArray;
                            try {
                                jsonArray = new JSONArray(readFile("[]",fgLibFileName));
                                fgLibObj = jsonArray;
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            renderFgLibToPage();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void renderSettingsToPage(){
        try {
            InputStream inputStream = context.getResources().openRawResource(R.raw.template_setting);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }

            template = new JSONArray(stringBuilder.toString());

            settingObj = new JSONObject(readFile("{}",settingFileName));

            RecyclerView recyclerView = findViewById(R.id.recyclerViewSetting);
            LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
            recyclerView.setLayoutManager(layoutManager);
            SettingAdapter.OnItemClickListener itemClickListener = position -> {
            };

            SettingAdapter adapter2 = new SettingAdapter(settingObj,template,itemClickListener);
            recyclerView.setAdapter(adapter2);

        } catch (JSONException | IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void saveSetting(){
        RecyclerView mRecyclerView = findViewById(R.id.recyclerViewSetting);
        RecyclerView.LayoutManager manager = mRecyclerView.getLayoutManager();

        try {
            for (int i = 0;i < template.length();i++){
                View myView = manager.findViewByPosition(i);

                JSONObject item = template.getJSONObject(i);
                EditText editText = myView.findViewById(R.id.editText);
                switch (item.getString("valueType")){
                    case "boolean":
                        Switch switchWid = myView.findViewById(R.id.switch1);
                        settingObj.put(item.getString("name"),switchWid.isChecked());
                        break;
                    case "double":
                        settingObj.put(item.getString("name"),Double.parseDouble(editText.getText().toString()));
                        break;
                    case "string":
                        settingObj.put(item.getString("name"),editText.getText().toString());
                        break;
                }

            }
            FileWriter writer = new FileWriter(settingFileName);
            writer.write(settingObj.toString());
            writer.close();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (NumberFormatException e){
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void onSaveButtonClick(View view){
        saveSetting();
    }

    public void onRestoreButtonClick(View view){
        new MaterialAlertDialogBuilder(context)
                .setTitle("Restore")
                .setMessage("Are you sure to restore all the settings?\nIt will also restart the app")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File file = new File(settingFileName);
                        file.delete();
                        //onBackPressed();
                        String packageName = getApplicationContext().getPackageName();
                        android.os.Process.killProcess(android.os.Process.myPid());
                        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

}