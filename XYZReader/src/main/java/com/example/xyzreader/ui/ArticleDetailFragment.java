package com.example.xyzreader.ui;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.ShareCompat;
import android.support.v7.graphics.Palette;
import android.support.v7.view.CollapsibleActionView;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.transition.Transition;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleMainActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ArticleDetailFragment";

    public static final String ARG_ITEM_ID = "item_id";
    public static final String ARG_PAGER_POSITION = "pager_position";
    public static final String ARG_STARTING_POSITION = "starting_position";
    private static final float PARALLAX_FACTOR = 1.25f;

    int TEXT_FADE_DURATION = 500;

    private Cursor mCursor;
    private long mItemId;
    private View mRootView;
    private int mMutedColor = 0xFF333333;
    private ObservableScrollView mScrollView;
    private DrawInsetsFrameLayout mDrawInsetsFrameLayout;
    private ColorDrawable mStatusBarColorDrawable;

    private int mTopInset;
    private View mPhotoContainerView;
    private ImageView mPhotoView;
    private int mScrollY;
    private boolean mIsCard = false;
    private int mStatusBarFullOpacityBottom;
    private int mPagerPosition;
    private int mStartingPosition;
    private CollapsingToolbarLayout mCollapsingToolbarLayout;

    TextView mTitleView;
    TextView mBylineView;

    boolean mIsTransitioning;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId,int startingPostion, int position) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        arguments.putInt(ARG_STARTING_POSITION,startingPostion);
        arguments.putInt(ARG_PAGER_POSITION,position);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }

        if(getArguments().containsKey(ARG_PAGER_POSITION)) {
            mPagerPosition = getArguments().getInt(ARG_PAGER_POSITION);
           // Log.e(TAG,"onCreate(): Pager position = " + mPagerPosition);
        }

        if(getArguments().containsKey(ARG_STARTING_POSITION)) {
            mStartingPosition = getArguments().getInt(ARG_STARTING_POSITION);
        }

       // Log.e(TAG,"Starting position = " + mStartingPosition + " Pager position = " + mPagerPosition);

        //See if we had a transition from the main activiy
        mIsTransitioning = savedInstanceState == null && mStartingPosition == mPagerPosition;

        mIsCard = getResources().getBoolean(R.bool.detail_is_card);
        mStatusBarFullOpacityBottom = getResources().getDimensionPixelSize(
                R.dimen.detail_card_top_margin);
        setHasOptionsMenu(true);
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_detail, container, false);

        mPhotoView = (ImageView) mRootView.findViewById(R.id.photo);
        mPhotoView.setTransitionName(ImageLoaderHelper.getTransitionName(getActivity(), mPagerPosition));
       // Log.e(TAG, "onCreateView(): Photo transition name = " + mPhotoView.getTransitionName());

        mTitleView = (TextView) mRootView.findViewById(R.id.article_title);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            if(mIsTransitioning) {
                mTitleView.setAlpha(0f);
                getActivity().getWindow().getSharedElementEnterTransition().addListener(new ImageTransitionListener() {
                    @Override
                    public void onTransitionEnd(Transition transition) {

                        //Fade in text
                      //  Log.e(TAG, "Transition Ended. Fade in title " + mTitleView.getText());
                        mTitleView.animate().setDuration(TEXT_FADE_DURATION).alpha(1f);
                    }
                });
            }
        }
      //  mPhotoContainerView = mRootView.findViewById(R.id.photo_container);

        mStatusBarColorDrawable = new ColorDrawable(0);

        //Get CollapsingToolbarLayout
        mCollapsingToolbarLayout = (CollapsingToolbarLayout)mRootView.findViewById(R.id.collapsing_toolbar);
        mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });

        bindViews();
       // updateStatusBar();


        return mRootView;
    }

    private void setupStartPostponedEnterTransition() {

      //  if (mAlbumPosition == mStartingPosition) {
            mPhotoView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    mPhotoView.getViewTreeObserver().removeOnPreDrawListener(this);
                    getActivity().supportStartPostponedEnterTransition();
                    return true;
                }
            });
       // }
    }
    private void updateStatusBar() {
        int color = 0;
        if (mPhotoView != null && mTopInset != 0 && mScrollY > 0) {
            float f = progress(mScrollY,
                    mStatusBarFullOpacityBottom - mTopInset * 3,
                    mStatusBarFullOpacityBottom - mTopInset);
            color = Color.argb((int) (255 * f),
                    (int) (Color.red(mMutedColor) * 0.9),
                    (int) (Color.green(mMutedColor) * 0.9),
                    (int) (Color.blue(mMutedColor) * 0.9));
        }
        mStatusBarColorDrawable.setColor(color);
       // mDrawInsetsFrameLayout.setInsetBackground(mStatusBarColorDrawable);
    }

    static float progress(float v, float min, float max) {
        return constrain((v - min) / (max - min), 0, 1);
    }

    static float constrain(float val, float min, float max) {
        if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        } else {
            return val;
        }
    }

    private void bindViews() {
        if (mRootView == null) {
            return;
        }


        mBylineView = (TextView) mRootView.findViewById(R.id.article_byline);
        mBylineView.setMovementMethod(new LinkMovementMethod());
        TextView bodyView = (TextView) mRootView.findViewById(R.id.article_body);
        bodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Rosario-Regular.ttf"));

        if (mCursor != null) {
            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1);


            bodyView.setText(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY)));
            ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                    .get(mCursor.getString(ArticleLoader.Query.PHOTO_URL), new ImageLoader.ImageListener() {
                        @Override
                        public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                            Bitmap bitmap = imageContainer.getBitmap();
                            if (bitmap != null && mCursor != null) {
                                Palette p = Palette.generate(bitmap, 12);
                                mMutedColor = p.getDarkMutedColor(0xFF333333);
                                mPhotoView.setImageBitmap(imageContainer.getBitmap());

                                //Setup title and byline after image has loaded
                                String title = mCursor.getString(ArticleLoader.Query.TITLE);
                                mTitleView.setText(title);

                                mBylineView.setText(Html.fromHtml(" By "
                                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                                + "<br>" +
                                                DateUtils.getRelativeTimeSpanString(
                                                mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
                                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                                DateUtils.FORMAT_ABBREV_ALL).toString()));

                                updateStatusBar();

                                mCollapsingToolbarLayout.setTitle(title);
                                mCollapsingToolbarLayout.setExpandedTitleColor(getResources().
                                        getColor(android.R.color.transparent));

                                int primaryColor = getResources().getColor(R.color.theme_primary);
                                int primaryDarkColor = getResources().getColor(R.color.theme_primary_dark);
                                mCollapsingToolbarLayout.setContentScrimColor(p.getMutedColor(primaryColor));
                                mCollapsingToolbarLayout.setStatusBarScrimColor(p.getDarkMutedColor(primaryDarkColor));

                                Log.e(TAG,"Downloaded image, start Postponed transition on " + title);
                                //Now that we've successfully loaded the image, we can start the
                                //shared transition.
                                getActivity().supportStartPostponedEnterTransition();
                            }
                        }

                        @Override
                        public void onErrorResponse(VolleyError volleyError) {

                        }
                    });
        } else {
            mRootView.setVisibility(View.GONE);
            //mTitleView.setText("N/A");
            //mBylineView.setText("N/A" );
            bodyView.setText("N/A");
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }



        bindViews();

    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }

    public int getUpButtonFloor() {
        if (mPhotoContainerView == null || mPhotoView.getHeight() == 0) {
            return Integer.MAX_VALUE;
        }

        // account for parallax
        return mIsCard
                ? (int) mPhotoContainerView.getTranslationY() + mPhotoView.getHeight() - mScrollY
                : mPhotoView.getHeight() - mScrollY;
    }

    /**
     * returns the shared image or null if it's not visible
     * @return shared imageview
     */
    @Nullable
    public ImageView getSharedImage() {
        if(isViewInBounds(getActivity().getWindow().getDecorView(),mPhotoView)) {
            return mPhotoView;
        }

        return null;
    }

    /**
     * Returns true if {@param view} is contained within {@param container}'s bounds.
     */
    private static boolean isViewInBounds(@NonNull View container, @NonNull View view) {
        Rect containerBounds = new Rect();
        container.getHitRect(containerBounds);
        return view.getLocalVisibleRect(containerBounds);
    }
}
