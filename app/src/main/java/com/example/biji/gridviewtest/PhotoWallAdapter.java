package com.example.biji.gridviewtest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.biji.gridviewtest.data.Images;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by biji on 2018/5/28.
 */

public class PhotoWallAdapter extends ArrayAdapter<String> implements AbsListView.OnScrollListener {
    /**
     * 記錄所有正在下載或等待下載的任務。
     */
//    private Set<BitmapWorkerTask> taskCollection;

    /**
     * 圖片緩存技術的核心類，用於緩存所有下載好的圖片，在程序內存達到設定值時會將最少最近使用的圖片移除掉。
     */
    private LruCache<String, Bitmap> mMemoryCache;

    /**
     * GridView的實例
     */
    private GridView mPhotoWall;

    /**
     * 第一張可見圖片的下標
     */
    private int mFirstVisibleItem;

    /**
     * 一屏有多少張圖片可見
     */
    private int mVisibleItemCount;

    /**
     * 記錄是否剛打開程序，用於解決進入程序不滾動屏幕，不會下載圖片的問題。
     */
    private boolean isFirstEnter = true;

    public PhotoWallAdapter(Context context, int textViewResourceId, String[] objects,
                            GridView photoWall) {
        super(context, textViewResourceId, objects);
        mPhotoWall = photoWall;
//        taskCollection = new HashSet<BitmapWorkerTask>();
        // 獲取應用程序最大可用內存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemory / 8;
        // 設置圖片緩存大小為程序最大可用內存的1/8
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
        mPhotoWall.setOnScrollListener(this);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final String url = getItem(position);
        View view;
        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.photo_layout, null);
        } else {
            view = convertView;
        }
        final ImageView photo = (ImageView) view.findViewById(R.id.photo);
        // 給ImageView設置一個Tag，保證異步加載圖片時不會亂序
        photo.setTag(url);
        setImageView(url, photo);
        return view;
    }

    /**
     * 給ImageView設置圖片。首先從LruCache中取出圖片的緩存，設置到ImageView上。如果LruCache中沒有該圖片的緩存，
     * 就給ImageView設置一張默認圖片。
     *
     * @param imageUrl  圖片的URL地址，用於作為LruCache的鍵。
     * @param imageView 用於顯示圖片的控件。
     */
    private void setImageView(String imageUrl, ImageView imageView) {
        Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(R.drawable.ic_image_black_24dp);
        }
    }

    /**
     * 將一張圖片存儲到LruCache中。
     *
     * @param key    LruCache的鍵，這裡傳入圖片的URL地址。
     * @param bitmap LruCache的鍵，這裡傳入從網絡上下載的Bitmap對象。
     */
    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemoryCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    /**
     * 從LruCache中獲取一張圖片，如果不存在就返回null。
     *
     * @param key LruCache的鍵，這裡傳入圖片的URL地址。
     * @return 對應傳入鍵的Bitmap對象，或者null。
     */
    public Bitmap getBitmapFromMemoryCache(String key) {
        return mMemoryCache.get(key);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        // 僅當GridView靜止時才去下載圖片，GridView滑動時取消所有正在下載的任務
        if (scrollState == SCROLL_STATE_IDLE) {
            loadBitmaps(mFirstVisibleItem, mVisibleItemCount);
        } else {
//            cancelAllTasks();
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                         int totalItemCount) {
        mFirstVisibleItem = firstVisibleItem;
        mVisibleItemCount = visibleItemCount;
        // 下載的任務應該由onScrollStateChanged裡調用，但首次進入程序時onScrollStateChanged並不會調用，
        // 因此在這裡為首次進入程序開啟下載任務。
        if (isFirstEnter && visibleItemCount > 0) {
            loadBitmaps(firstVisibleItem, visibleItemCount);
            isFirstEnter = false;
        }
    }

    /**
     * 加載Bitmap對象。此方法會在LruCache中檢查所有屏幕中可見的ImageView的Bitmap對象，
     * 如果發現任何一個ImageView的Bitmap對象不在緩存中，就會開啟異步線程去下載圖片。
     *
     * @param firstVisibleItem 第一個可見的ImageView的下標
     * @param visibleItemCount 屏幕中總共可見的元素數
     */
    private void loadBitmaps(int firstVisibleItem, int visibleItemCount) {
        try {
            for (int i = firstVisibleItem; i < firstVisibleItem + visibleItemCount; i++) {
                String imageUrl = Images.imageThumbUrls[i];
                Bitmap bitmap = getBitmapFromMemoryCache(imageUrl);
                if (bitmap == null) {
//                    BitmapWorkerTask task = new BitmapWorkerTask();
//                    taskCollection.add(task);
//                    task.execute(imageUrl);
                    addBitmap(imageUrl);
                } else {
                    ImageView imageView = (ImageView) mPhotoWall.findViewWithTag(imageUrl);
                    if (imageView != null && bitmap != null) {
//                        imageView.setImageBitmap(bitmap);
                        Glide.with(getContext())
                                .load(bitmap)
                                .into(imageView);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    /**
//     * 取消所有正在下載或等待下載的任務。
//     */
//    public void cancelAllTasks() {
//        if (taskCollection != null) {
//            for (BitmapWorkerTask task : taskCollection) {
//                task.cancel(false);
//            }
//        }
//    }

//    private void addBitmap(final String imageUrl) {
//        Flowable.just(imageUrl)
//                .subscribeOn(Schedulers.newThread())
//                .map(new Function<String, Bitmap>() {
//                    @Override
//                    public Bitmap apply(String s) throws Exception {
//                        return Glide.with(getContext())
//                                .asBitmap()
//                                .load(imageUrl)
//                                .submit()
//                                .get();
//                    }
//                }).observeOn(AndroidSchedulers.mainThread())
//                .subscribe(new Consumer<Bitmap>() {
//            @Override
//            public void accept(Bitmap bitmap) throws Exception {
//                addBitmapToMemoryCache(imageUrl, bitmap);
//                // 根據Tag找到相應的ImageView控件，將下載好的圖片顯示出來。
//                ImageView imageView = (ImageView) mPhotoWall.findViewWithTag(imageUrl);
//                if (imageView != null && bitmap != null) {
//                    imageView.setImageBitmap(bitmap);
//                }
//            }
//        });
//    }
    private void addBitmap(final String imageUrl) {
        Completable.fromAction(new Action() {
            @Override
            public void run() throws Exception {
                // 根據Tag找到相應的ImageView控件，將下載好的圖片顯示出來。
                final ImageView imageView = (ImageView) mPhotoWall.findViewWithTag(imageUrl);
                Glide.with(getContext())
                        .asBitmap()
                        .load(imageUrl)
                        .into(new SimpleTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                                addBitmapToMemoryCache(imageUrl, bitmap);
                                if (imageView != null && bitmap != null) {
                                    imageView.setImageBitmap(bitmap);
                                }
                            }
                        });
            }
        }).subscribe();
    }

//    /**
//     * 異步下載圖片的任務。
//     *
//     * @author guolin
//     */
//    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
//
//        /**
//         * 圖片的URL地址
//         */
//        private String imageUrl;
//
//        @Override
//        protected Bitmap doInBackground(String... params) {
//            imageUrl = params[0];
//            // 在後台開始下載圖片
//            Bitmap bitmap = null;
//            try {
//                bitmap = downloadBitmap(params[0]);
//            } catch (ExecutionException e) {
//                e.printStackTrace();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            if (bitmap != null) {
//                // 圖片下載完成後緩存到LrcCache中
//                addBitmapToMemoryCache(params[0], bitmap);
//            }
//            return bitmap;
//        }
//
//        @Override
//        protected void onPostExecute(Bitmap bitmap) {
//            super.onPostExecute(bitmap);
//            // 根據Tag找到相應的ImageView控件，將下載好的圖片顯示出來。
//            ImageView imageView = (ImageView) mPhotoWall.findViewWithTag(imageUrl);
//            if (imageView != null && bitmap != null) {
//                imageView.setImageBitmap(bitmap);
//            }
//            taskCollection.remove(this);
//        }
//
//        /**
//         * 建立HTTP請求，並獲取Bitmap對象。
//         *
//         * @param imageUrl 圖片的URL地址
//         * @return 解析後的Bitmap對象
//         */
//        private Bitmap downloadBitmap(String imageUrl) throws ExecutionException, InterruptedException {
//            Bitmap bitmap = null;
////            HttpURLConnection con = null;
////            try {
////                URL url = new URL(imageUrl);
////                con = (HttpURLConnection) url.openConnection();
////                con.setConnectTimeout(5 * 1000);
////                con.setReadTimeout(10 * 1000);
////                bitmap = BitmapFactory.decodeStream(con.getInputStream());
////            } catch (Exception e) {
////                e.printStackTrace();
////            } finally {
////                if (con != null) {
////                    con.disconnect();
////                }
////            }
//            bitmap = Glide.with(getContext())
//                    .asBitmap()
//                    .load(imageUrl)
//                    .submit()
//                    .get();
//            return bitmap;
//        }
//
//    }
}
