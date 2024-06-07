package com.wyywn.fgcam;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FgLibAdapter extends RecyclerView.Adapter<FgLibAdapter.ViewHolder> {

    private JSONArray mData;
    private OnItemClickListener mlistener;

    public FgLibAdapter(JSONArray data, OnItemClickListener listener) {
        mData = data;
        mlistener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.fglib_layout, parent, false);
        return new ViewHolder(view, mlistener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        try {
            JSONObject item = mData.getJSONObject(position);

            holder.button.setText(item.getString("name"));

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getItemCount() {
        return mData.length();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView button;
        private OnItemClickListener listener;

        public ViewHolder(View itemView,OnItemClickListener listener) {
            super(itemView);
            button = itemView.findViewById(R.id.textView);

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
