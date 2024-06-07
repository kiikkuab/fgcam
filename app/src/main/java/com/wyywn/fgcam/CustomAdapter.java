package com.wyywn.fgcam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class CustomAdapter extends RecyclerView.Adapter<CustomAdapter.ViewHolder> {

    private ArrayList<String> mData;
    private OnItemClickListener mlistener;

    public CustomAdapter(ArrayList<String> data, OnItemClickListener listener) {
        mData = data;
        mlistener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_layout, parent, false);
        return new ViewHolder(view, mlistener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        String item = mData.get(position);

        String[] result = item.split("/");
        holder.textView.setText(result[result.length-1]);

        Bitmap bmp= BitmapFactory.decodeFile(item);
        Bitmap croppedBitmap = bmp;
        if (bmp.getHeight() > bmp.getWidth() * 1.4){
            croppedBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), (int) (bmp.getWidth() * 1.4));
        }

        holder.imageView.setImageBitmap(croppedBitmap);

    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView textView;
        public ImageView imageView;
        private OnItemClickListener listener;

        public ViewHolder(View itemView,OnItemClickListener listener) {
            super(itemView);
            textView = itemView.findViewById(R.id.textView);
            imageView = itemView.findViewById(R.id.imageView);

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
