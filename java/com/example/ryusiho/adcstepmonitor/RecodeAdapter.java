package com.example.ryusiho.adcstepmonitor;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.ryusiho.adcstepmonitor.R;

import java.util.List;

/**
 * RecyclerView Adapter
 */
public class RecodeAdapter extends RecyclerView.Adapter<RecodeAdapter.MyViewHolder> {

    /* 트래킹 정보를 담고있는 리스트 */
    private List<RecodeInfo> recodeList;

    public class MyViewHolder extends RecyclerView.ViewHolder {
        public TextView locView, timeView, stepsView, movingView;

        /* 기록된 정보들에 대한 레이아웃 초기화 */
        public MyViewHolder(View view) {
            super(view);
            locView = (TextView) view.findViewById(R.id.locView);
            timeView = (TextView) view.findViewById(R.id.timeView);
            stepsView = (TextView) view.findViewById(R.id.stepsView);
            movingView = (TextView) view.findViewById(R.id.movingView);
        }
    }

    /* 기록정보 리스트를 가져올 생성자 */
    public RecodeAdapter(List<RecodeInfo> recodeList) {
        this.recodeList = recodeList;
    }

    /* 레이아웃 inflate */
    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recode_list_row, parent, false);

        return new MyViewHolder(itemView);
    }

    /* 레이아웃 기능 */
    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        RecodeInfo recodeInfo = recodeList.get(position);
        Log.d("recodeAdapter : ", holder.locView.getText().toString());
        holder.timeView.setText(recodeInfo.getStartTime()+" ~ "+recodeInfo.getEndTime() );
        holder.locView.setText(recodeInfo.getLocation());
        holder.stepsView.setText("Steps : " + recodeInfo.getSteps()+" ("+recodeInfo.getMovingTime()+"분)");
        if(recodeInfo.getIsMoving())
            holder.movingView.setText("이동");
        else
            holder.movingView.setText("정지");
    }

    /* 리스트 아이템 개수 반환 */
    @Override
    public int getItemCount() {
        return recodeList.size();
    }
}
