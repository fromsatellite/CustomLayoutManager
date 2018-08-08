package com.satellite.layoutmanager;

import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;

public class FlowLayoutManager extends RecyclerView.LayoutManager {

    // 保存所有item信息
    /**
     * 内容的总高度
     */
    private int totalHeight = 0;
    private SparseArray<Rect> allItemFrames;
    private SparseBooleanArray allItemsAttached;
    /**
     * 滑动的偏移量，类似于ScrollY
     */
    private int verticalScrollOffset = 0;

    public FlowLayoutManager() {
//        setAutoMeasureEnabled(true);
        allItemFrames = new SparseArray<>();
        allItemsAttached = new SparseBooleanArray();
    }

    // setAutoMeasureEnabled(true); deprecated
    @Override
    public boolean isAutoMeasureEnabled() {
        return true;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // 初始化调用两次
    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        Log.d("@@@", "onLayoutChildren");
//        super.onLayoutChildren(recycler, state);
        // 摆放child
        if (getItemCount() <= 0){
            return;
        }
        // if RecyclerView is in pre-layout step,支持动画，直接跳过
        if (state.isPreLayout()){
            return;
        }
        // 分离视图并放入Recycler的scrap缓存中，以备将来复用(优先于回收池)
        detachAndScrapAttachedViews(recycler);

        // Y偏移量
        int offsetY = 0;
        // X偏移量
        int offsetX = 0;
        // 当前Item的高度
        int viewH = 0;
        for (int i = 0;i < getItemCount();i++){
            View view = recycler.getViewForPosition(i);
            // 测量之前先添加
            addView(view);
            measureChildWithMargins(view, 0, 0);

            int w = getDecoratedMeasuredWidth(view);
            int h = getDecoratedMeasuredHeight(view);

            Rect itemFram = allItemFrames.get(i);
            if (itemFram == null){
                itemFram = new Rect();
            }
            // 流式布局，每行会有多个item
            if (offsetX + w > getWidth()){ // 需要换行
                // 换行后偏移量重置
                offsetX = w;
                // 换行后offsetY增加上一行的高度viewH，作为新一行的top
                offsetY += viewH;

                itemFram.set(0, offsetY, w, offsetY + h);
            } else { // 不用换行
                itemFram.set(offsetX, offsetY, offsetX + w, offsetY + h);
                offsetX += w;
            }
            // 保存item的布局信息
            allItemFrames.put(i, itemFram);
            // 保存item的attach状态
            allItemsAttached.put(i, false);
            // 记录当前item的高度
            viewH = h;
        }
        // 总高度(注意：不管最后一个item是否存在换行，offsetY此时表示最后一行的top,所以要加上item的高度)
        totalHeight = offsetY + viewH;

        // detach   轻量级的移除操作    remove  重量级
        // 回收不可见的，并显示可见区域的view(不调用此方法，屏幕上上空白的)
        recyclerViewFillView(recycler, state);
    }

    // getChildCount()返回的是attached的item个数，不包括detached and/or scrapped
    // getItemCount()返回的是adapter真实的item个数
    private void recyclerViewFillView(RecyclerView.Recycler recycler, RecyclerView.State state) {
        // 分离视图并放入Recycler的scrap缓存中，以备将来复用(优先于回收池)
//        detachAndScrapAttachedViews(recycler);
        // 计算屏幕可见的内容区域(第一个item从(0,0)开始)
        Rect displayFrame = new Rect(0, verticalScrollOffset, getWidth(), verticalScrollOffset + getHeight());
        // 屏幕区域
        Rect windowRect = new Rect(0, 0, getWidth(), getHeight());

        // 第一步:将滑出屏幕的view进行回收
        // 注意：getChildCount()方法的注释： Return the current number of child views attached to the parent RecyclerView.
        // This does not include child views that were temporarily detached and/or scrapped.
        // 如果在之前调用了detachAndScrapAttachedViews(recycler)，getChildCount()返回0
        Log.d("@@@", "recyclerViewFillView getChildCount = "+getChildCount());
        Rect childFrame = new Rect();
        for (int i = 0;i<getChildCount();i++){
            View childView = getChildAt(i);

            // getChildCount一般小于item总数，这里的下标i和position没关系，so下面的逻辑上错误的
//            Rect childRect = allItemFrames.get(i);
//            // 如果两个区域不相交，即没有交集
//            if (!Rect.intersects(displayFrame, childRect)){
//                removeAndRecycleView(childView, recycler);
//            }

            // 获取item的真实位置，remove回收，记录attach为false
            int position = getPosition(childView);
            childFrame.left = childView.getLeft();
            childFrame.top = getDecoratedTop(childView);
            childFrame.right = getDecoratedRight(childView);
            childFrame.bottom = getDecoratedBottom(childView);
            if (!Rect.intersects(windowRect, childFrame)){
                removeAndRecycleView(childView, recycler);
                allItemsAttached.put(position, false);
            }
        }

        // 第二步:绘制屏幕上可见区域的子view
        Log.d("@@@", "recyclerViewFillView getItemCount = "+getItemCount());
        for (int j = 0; j < getItemCount(); j++){
            Rect frame = allItemFrames.get(j);
            // 优化：滑动后如果item仍然可见，则不重复添加
            if (!allItemsAttached.get(j) && Rect.intersects(displayFrame, frame)){
                // scrap回收池里面拿的
                View scrap = recycler.getViewForPosition(j);
                measureChildWithMargins(scrap,0,0);
                addView(scrap);
                allItemsAttached.put(j, true);
                // 关键方法，child显示在recyclerview上
                layoutDecorated(scrap, frame.left, frame.top - verticalScrollOffset,
                        frame.right, frame.bottom - verticalScrollOffset);
            }

        }
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        Log.d("@@@", "scrollVerticallyBy dy = " + dy);
        // 此方法会detach item，并将item存进scrap缓存,慎用
//        detachAndScrapAttachedViews(recycler);
        //实际滑动距离  dy, 往上滑 dy>0, 往下滑，dy<0
        int trval = dy;
//        如果滑动到最顶部  往下滑   verticalScrollOffset   -
//        第一个坐标值 减 以前最后一个坐标值  //记死
        if (verticalScrollOffset + dy < 0) { // 往下滑，第一条数据出现在屏幕顶部时,不可滑动
            trval = -verticalScrollOffset;
        } else if(verticalScrollOffset + dy > totalHeight - getHeight()){ // 往上滑,最后一条数据出现在屏幕底部，不可滑动
//            如果滑动到最底部  往上滑   verticalScrollOffset   +
            trval = totalHeight - getHeight() - verticalScrollOffset;
        }

//        边界值判断
        verticalScrollOffset += trval;

        // 平移容器内的item，此方法作用:往上滑参数dy>0,所有child向下偏移;往下滑参数dy<0,所有child向上偏移
        offsetChildrenVertical(-trval);
        // TODO offsetChildrenVertical可能和layoutDecorated存在重复绘制
        recyclerViewFillView(recycler, state);
        return trval;
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }
}
