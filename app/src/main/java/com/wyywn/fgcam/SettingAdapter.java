package com.wyywn.fgcam;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SettingAdapter extends RecyclerView.Adapter<SettingAdapter.ViewHolder> {

    private JSONObject settingObj ;
    private JSONArray templateArr;
    private OnItemClickListener mlistener;

    public SettingAdapter(JSONObject setting, JSONArray template, OnItemClickListener listener) {
        settingObj = setting;
        templateArr = template;
        mlistener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.setting_layout, parent, false);
        return new ViewHolder(view, mlistener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        try {
            JSONObject item = templateArr.getJSONObject(position);

            holder.textView.setText(item.getString("description"));

            switch (item.getString("valueType")){
                case "boolean":
                    holder.switchWid.setVisibility(View.VISIBLE);
                    holder.switchWid.setChecked(settingObj.getBoolean(item.getString("name")));
                    break;
                case "double":
                    holder.editText.setVisibility(View.VISIBLE);
                    holder.editText.setText(Double.toString(settingObj.getDouble(item.getString("name"))));
                    break;
                case "string":
                    holder.editText.setVisibility(View.VISIBLE);
                    holder.editText.setText(settingObj.getString(item.getString("name")));
                    break;
            }

        } catch (JSONException e) {

            throw new RuntimeException(e);
        }
    }

    @Override
    public int getItemCount() {
        return templateArr.length();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView textView;
        public Switch switchWid;
        public EditText editText;
        private OnItemClickListener listener;

        public ViewHolder(View itemView,OnItemClickListener listener) {
            super(itemView);
            textView = itemView.findViewById(R.id.textView1);
            switchWid = itemView.findViewById(R.id.switch1);
            editText = itemView.findViewById(R.id.editText);

            this.listener = listener;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION && listener != null) {
                listener.onItemClick(position);
            }
        }
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }
}
