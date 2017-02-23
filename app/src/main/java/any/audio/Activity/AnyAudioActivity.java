package any.audio.Activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.Util;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.ArrayList;

import any.audio.Adapters.PlaylistAdapter;
import any.audio.Adapters.SearchResultsAdapter;
import any.audio.Config.AppConfig;
import any.audio.Config.Constants;
import any.audio.Fragments.DownloadedFragment;
import any.audio.Fragments.DownloadingFragment;
import any.audio.Fragments.DownloadsFragment;
import any.audio.Fragments.ExploreFragment;
import any.audio.Fragments.NavigationDrawerFragment;
import any.audio.Fragments.SearchFragment;
import any.audio.Fragments.SettingsFragment;
import any.audio.Fragments.ShowAllFragment;
import any.audio.Managers.FontManager;
import any.audio.Models.PlaylistItem;
import any.audio.Network.ConnectivityUtils;
import any.audio.R;
import any.audio.SharedPreferences.SharedPrefrenceUtils;
import any.audio.helpers.CircularImageTransformer;
import any.audio.helpers.L;
import any.audio.helpers.StreamUrlFetcher;
import any.audio.helpers.PlaylistGenerator;
import any.audio.helpers.QueueManager;
import any.audio.helpers.QueueManager.QueueEventListener;
import any.audio.helpers.ScreenDimension;
import any.audio.helpers.TaskHandler;
import any.audio.helpers.ToastMaker;
import any.audio.services.AnyAudioStreamService;
import any.audio.services.NotificationPlayerService;
import de.hdodenhof.circleimageview.CircleImageView;

import static any.audio.services.AnyAudioStreamService.anyPlayer;

public class AnyAudioActivity extends AppCompatActivity implements PlaylistGenerator.PlaylistGenerateListener, SearchResultsAdapter.SearchResultActionListener, PlaylistAdapter.PlaylistItemListener, QueueEventListener {

    public static final int FRAGMENT_EXPLORE = 1;
    public static final int FRAGMENT_SEARCH = 2;
    public static final int FRAGMENT_DOWNLOADS = 3;
    public static final int FRAGMENT_SETTINGS = 5;
    public static final int FRAGMENT_ABOUT_US = 6;
    public static final int FRAGMENT_SHOW_ALL = 7;
    public static final int FRAGMENT_UPDATES = 8;

    final String playBtnString = "\uE037";
    final String pauseBtnString = "\uE034";
    final String suffleBtnString = "\uE043";
    final String repeatAllBtnString = "\uE627";
    final String noRepeatBtnString = "\uE628";
    final String[] repeatModesList = {suffleBtnString, repeatAllBtnString, noRepeatBtnString};
    final String[] repeatModesTexts = {"Suffle", "Repeat All", "No Repeat"};
    private CircleImageView thumbnail;
    private TextView nextBtn;
    private SwitchCompat autoplaySwitch;
    private TextView pauseBtn;
    private TextView header;
    private TextView playlistBtn;
    private TextView pushDown;
    private TextView title;
    private TextView repeatModeBtn;
    private TextView artist;
    private TextView title_second;
    private TextView artist_second;
    private View view;
    private ImageView homeIcon;
    FrameLayout playerBg;
    SeekBar seekBar;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

    private Typeface typeface;
    public static AnyAudioActivity anyAudioActivityInstance;
    private SlidingUpPanelLayout mLayout;
    private TextView streamDuration;
    private TextView homePanelTitle;
    private static ExoPlayer exoPlayer;
    private SharedPrefrenceUtils utils;
    private int UP_NEXT_PREPARE_TIME_OFFSET = 50000;
    private int mBuffered = -1;
    private AnyAudioPlayer mPlayerThread;
    private StreamProgressUpdateBroadcastReceiver streamProgressUpdpateReceiver;
    private NotificationPlayerStateBroadcastReceiver notificationPlayerStateReceiver;
    private StreamUriBroadcastReceiver streamUriReceiver;
    private SongActionBroadcastListener songActionReceiver;
    private boolean receiverRegistered = false;
    private ProgressBar progressBarStream;
    private ProgressBar playlistPreparingProgressbar;
    private RecyclerView playlistRecyclerView;
    private PlaylistAdapter playlistAdapter;
    private PlaylistGenerator playlistGenerator;
    private QueueManager queueManager;
    private QueueEventListener queueEventListener;
    private TextView playlistMessagePanel;
    private RelativeLayout playerPlaceHolderView;
    private boolean backPressedOnce  = false;
    private PrepareBottomPlayerBroadcastReceiver prepareBottomPlayerReceiver;
    private TextView repeatModeText;
    private Toolbar toolbar;
    private TextView searchIcon;
    private FrameLayout navFrag;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.light_main_page);
        anyAudioActivityInstance = this;
        setSupportActionBar((Toolbar) findViewById(R.id.home_toolbar));
        getSupportActionBar().setTitle("");
        initView();
        //  handleIntent();
        ScreenDimension.getInstance(this).init();
        configureStorageDirectory(savedInstanceState);
        utils = SharedPrefrenceUtils.getInstance(this);


    }

    private void configureStorageDirectory(Bundle savedInstance) {

        if (savedInstance == null) {
            L.m("Home configureStorageDirectory()", "making dirs");
            AppConfig.getInstance(this).configureDevice();
        }
    }

    @Override
    protected void onResume() {

        super.onResume();
        backPressedOnce = false;

        if (!receiverRegistered) {
            registerReceivers();
        }
        queueManager = QueueManager.getInstance(this);
        queueManager.setQueueEventListener(this);
        handleIntent();

        playlistAdapter = PlaylistAdapter.getInstance(this);

        if (utils.getLastItemThumbnail().length() > 0) {
            // here param could be any thing among last item (artist,url,title)
            //set visibility of placeholder GONE
            playerPlaceHolderView.setVisibility(View.GONE);
            //set Bottom Player Slidable
            mLayout.setEnabled(true);
            mLayout.setClickable(true);
            // prepare the player according to last items
            prepareBottomPlayer();
        }else{
            // user has not played any song so disable to scroll bottom view
            mLayout.setEnabled(false);
            mLayout.setClickable(false);

        }

        if (utils.getAutoPlayMode()) {
            triggerAutoPlayMode();
        } else {
            triggerQueueMode();
        }

        prepareNavigationDrawer();

    }

    @Override
    public void onBackPressed() {

        if (mLayout != null &&
                (mLayout.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED || mLayout.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED)) {
            mLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        }

        if(getSupportActionBar().getTitle().equals("SETTINGS") || getSupportActionBar().getTitle().equals("DOWNLOADING") || getSupportActionBar().getTitle().equals("DOWNLOADED")){
            // transact search or explore
            normalStart();
            return;
        }

        if (!backPressedOnce) {
            Toast.makeText(this, "Press Back Once More to Exit", Toast.LENGTH_LONG).show();
            backPressedOnce = true;
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.any_audio_menu_popup, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        switch (id) {

            case R.id.feedback:

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/html");
                intent.putExtra(Intent.EXTRA_EMAIL, "anyaudio.in@gmail.com");
                intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback");
                intent.putExtra(Intent.EXTRA_TEXT, "");
                startActivity(Intent.createChooser(intent, "Send Email"));

                return true;

            case R.id.exit:
                    finish();
                return true;

        }

        return true;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    private void showExploreItemPopUpMenu(View view, final String v_id, final String youtubeId, final String title, final String artist) {

        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.explore_card_popup, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {

                onAddToQueue(v_id,youtubeId,title,artist);

                ToastMaker.getInstance(AnyAudioActivity.this).toast("\""+title+ "\" Added To Queue");

                return true;
            }
        });
        popup.show();

    }

    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    protected void onPause() {
        super.onPause();

        if (receiverRegistered) {
            unRegisterReceivers();
        }

    }

    @Override
    public void onStop() {
        super.onStop();

    }

    @Override
    public void onPlayAction(String video_id, String title) {

        Log.d("StreamTestNew", " action play " + video_id);
        initStream(video_id, title);

    }

    @Override
    public void onDownloadAction(String video_id, String title, String thumb, String artist) {

        Log.d("ExploreCard"," dnd tapped");
        showDownloadDialog(video_id, title, thumb, artist);

    }

    @Override
    public void onAddToQueue(String video_id, String youtubeId, String title, String uploader) {

        queueManager.pushQueueItem(new PlaylistItem(video_id, youtubeId, title, uploader), true);

    }

    public void onShowAll(String type) {

        transactFragment(FRAGMENT_SHOW_ALL,type,"<mode>");

    }

    public void onPopUpMenuTap(View view,String video_id,String youtubeId,String title,String uploader) {
        Log.d("ExploreCard"," pop tapped");
        showExploreItemPopUpMenu(view,video_id,youtubeId,title,uploader);
    }

    @Override
    public void onPlaylistPreparing() {

        // hide the current playlist items  and make the current progresh bar visible Only if AutoPlay is On

        //Ensuring that it is called on Main UI Thread
        if (utils.getAutoPlayMode()) {
            new View(this).post(new Runnable() {
                @Override
                public void run() {
                    playlistRecyclerView.setVisibility(View.GONE);
                    playlistMessagePanel.setVisibility(View.GONE);
                    playlistPreparingProgressbar.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    @Override
    public void onPlaylistPrepared(ArrayList<PlaylistItem> items) {

        // dismiss the progress bar and reset the adapter data Only if AutoPlay is On
        if (utils.getAutoPlayMode()) {
            playlistPreparingProgressbar.setVisibility(View.GONE);
            playlistMessagePanel.setVisibility(View.GONE);
            playlistRecyclerView.setVisibility(View.VISIBLE);
            playlistAdapter.setPlaylistItem(items);
            playlistRecyclerView.setAdapter(playlistAdapter);
        }

    }

    @Override
    public void onPlaylistItemTapped(PlaylistItem item) {

        // stream current item
        initStream(item.videoId, item.getTitle());
        //set CurrentItem Info
        utils.setCurrentItemStreamUrl(item.getVideoId());
        utils.setCurrentItemTitle(item.getTitle());
        utils.setCurrentItemThumbnailUrl(getImageUrl(item.getYoutubeId()));
        utils.setCurrentItemArtist(item.getUploader());

    }

    @Override
    public void onQueueItemPop() {
        // remove the top item from adapter if queue is currently playing
        if (!utils.getAutoPlayMode())
            playlistAdapter.popItem();

    }

    @Override
    public void onQueueItemPush(PlaylistItem item) {
        // add new item to top of adapter if queue is currently playing
        if (!utils.getAutoPlayMode())
            playlistAdapter.appendItem(item);

    }

    private void handleIntent() {

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {

            String type = bundle.getString("type");
            String term = bundle.getString("term");

            if (type != null) {

                if (type.equals("search")) {
                    SharedPrefrenceUtils.getInstance(this).setLastSearchTerm(term);
                    L.m("AnyAudioHome", " invoking action search");
                    homePanelTitle.setText(reformatHomeTitle(term));
                    transactFragment(FRAGMENT_SEARCH, term,Constants.SEARCH_MODE_SERVER);
                }

            } else {
                normalStart();
            }
        } else {
            normalStart();
        }
    }

    private void normalStart() {
        String term = utils.getLastSearchTerm();
        if (utils.isFirstSearchDone()) {
            setTitle(reformatHomeTitle(term));
            //todo: remove the mode dependency since it is not feasible for every func.
            Log.d("SearchTest"," is new "+utils.isNewSearch());
            String mode = utils.isNewSearch()?Constants.SEARCH_MODE_SERVER:Constants.SEARCH_MODE_DB;
            transactFragment(FRAGMENT_SEARCH, term,mode);
        } else {
            transactFragment(FRAGMENT_EXPLORE, "EXPLORE","<not required>");
        }
    }

    private String reformatHomeTitle(String term) {
        String t = (term.length() > 21) ? term.substring(0, 18) + "..." : term;
        return t;
    }

    private void setTitle(String title){
        header.setText(title);
    }

    private void initView() {

        mLayout = (SlidingUpPanelLayout) findViewById(R.id.sliding_layout);
        mLayout.setDragView(R.id.playlistBtn);

        mLayout.addPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {

                transformThumbnail(slideOffset);
                transformControl(slideOffset);
                transformInfo(slideOffset);
                Log.d("SildePanel", "offset " + slideOffset);

            }

            @Override
            public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {

                Log.d("SlidePanel", " onPanelStateChanged " + newState);

            }
        });

        mLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

        playlistGenerator = PlaylistGenerator.getInstance(AnyAudioActivity.this);
        playlistGenerator.setPlaylistGenerationListener(this);

       // homeIcon = (ImageView) findViewById(R.id.homeIcon);
        typeface = FontManager.getInstance(this).getTypeFace(FontManager.FONT_MATERIAL);
        toolbar = (Toolbar) findViewById(R.id.home_toolbar);
        setSupportActionBar(toolbar);
        searchIcon = (TextView) findViewById(R.id.searchIconTxt);
        searchIcon.setTypeface(typeface);

        searchIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent search = new Intent(AnyAudioActivity.this, SearchActivity.class);
                startActivity(search);

            }
        });

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,toolbar,
                R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        mDrawerLayout.setDrawerListener(mDrawerToggle);

        progressBarStream = (ProgressBar) findViewById(R.id.progressBarStreamProgress);
        playlistMessagePanel = (TextView) findViewById(R.id.playlistMessagePanel);
        playerPlaceHolderView = (RelativeLayout) findViewById(R.id.welcome_placeholderView);
        homePanelTitle = (TextView) findViewById(R.id.homePanelTitle);
        repeatModeBtn = (TextView) findViewById(R.id.repeatModeBtn);
        navFrag = (FrameLayout) findViewById(R.id.left_drawer);
        repeatModeText = (TextView) findViewById(R.id.repeatModeText);
        view = findViewById(R.id.welcome_placeholderView);
        repeatModeBtn.setTypeface(typeface);
        autoplaySwitch = (SwitchCompat) findViewById(R.id.autoplay_switch);
        playerBg = (FrameLayout) findViewById(R.id.playerBgFrame);
        header = (TextView) findViewById(R.id.header);
        thumbnail = (CircleImageView) findViewById(R.id.thumbnail);
        nextBtn = (TextView) findViewById(R.id.nextBtn);
        pauseBtn = (TextView) findViewById(R.id.pauseBtn);
        playlistBtn = (TextView) findViewById(R.id.playlistBtn);
        pushDown = (TextView) findViewById(R.id.pushDown);
        streamDuration = (TextView) findViewById(R.id.stream_duration);
        seekBar = (SeekBar) findViewById(R.id.seekBar);

        //playlist view
        playlistPreparingProgressbar = (ProgressBar) findViewById(R.id.auto_play_or_queue_progress_bar);
        playlistRecyclerView = (RecyclerView) findViewById(R.id.autoplay_or_queue_recycler_view);
        playlistRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        playlistRecyclerView.setHasFixedSize(true);



        //font face
        nextBtn.setTypeface(typeface);
        pauseBtn.setTypeface(typeface);
        playlistBtn.setTypeface(typeface);
        pushDown.setTypeface(typeface);

        title = (TextView) findViewById(R.id.title);
        artist = (TextView) findViewById(R.id.artist);
        title_second = (TextView) findViewById(R.id.title_second);
        artist_second = (TextView) findViewById(R.id.artist_second);

        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams((int) inPx(36), (int) inPx(36));
        controlParams.setMargins(0, (int) inPx(6), 0, 0);

        pauseBtn.setLayoutParams(controlParams);
        nextBtn.setLayoutParams(controlParams);
        playlistBtn.setLayoutParams(controlParams);

        RelativeLayout.LayoutParams infoParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.setMargins(0, (int) inPx(12), 0, 0);
        title.setLayoutParams(infoParams);

//        homeIcon.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                transactFragment(FRAGMENT_EXPLORE,"EXPLORE");
//            }
//        });

        playlistBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(mLayout.getPanelState()==SlidingUpPanelLayout.PanelState.COLLAPSED)
                    mLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);

            }
        });

        pushDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mLayout.setEnabled(true);
                mLayout.setClickable(true);
                mLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            }
        });

        repeatModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                int nextMode = getNextRepeatModeIndex();
                repeatModeBtn.setText(repeatModesList[nextMode]);
                repeatModeText.setText(repeatModesTexts[nextMode]);

            }
        });

        autoplaySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {

                playlistPreparingProgressbar.setVisibility(View.VISIBLE);
                playlistRecyclerView.setVisibility(View.INVISIBLE);
                utils.setAutoPlayMode(checked);
                if (checked) {
                    triggerAutoPlayMode();
                } else {
                    triggerQueueMode();
                }

            }
        });

    }

    public void onNavItemSelected(int fragmentToTransact,String title){
        transactFragment(fragmentToTransact,title,"<mode>");
        mDrawerLayout.closeDrawers();
    }

    private void prepareNavigationDrawer(){

        //transact fragment
        NavigationDrawerFragment navigationDrawerFragment = new NavigationDrawerFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.left_drawer, navigationDrawerFragment)
                .commitAllowingStateLoss();

    }

    private void triggerAutoPlayMode() {

        // disable repeat modes while autoplay
        repeatModeBtn.setClickable(false);
        repeatModeText.setVisibility(View.GONE);
        repeatModeBtn.setVisibility(View.GONE);

        // get auto Playlist
        ArrayList<PlaylistItem> items = PlaylistGenerator.getInstance(AnyAudioActivity.this).getPlaylistItems();
        if (items.size() == 0) {

            playlistMessagePanel.setVisibility(View.VISIBLE);
            playlistRecyclerView.setVisibility(View.GONE);
            playlistPreparingProgressbar.setVisibility(View.GONE);
            playlistMessagePanel.setVisibility(View.VISIBLE);
            playlistMessagePanel.setText("Cannot Fetch UpNext.. ! ");

        } else {

            playlistMessagePanel.setVisibility(View.GONE);
            playlistAdapter.setPlaylistItem(items);
            playlistRecyclerView.setAdapter(playlistAdapter);
            playlistPreparingProgressbar.setVisibility(View.GONE);
            playlistRecyclerView.setVisibility(View.VISIBLE);

        }
    }

    private void triggerQueueMode() {
        // get Queued Items if no items present show the message to add queue
        ArrayList<PlaylistItem> queuedItems = queueManager.getQueue();
        repeatModeBtn.setVisibility(View.VISIBLE);
        repeatModeText.setVisibility(View.VISIBLE);
        repeatModeText.setText(getCurrentRepeatModeText());
        repeatModeBtn.setText(getCurrentRepeatModeBtnText());

        if (queuedItems.size() == 0) {
            playlistMessagePanel.setVisibility(View.VISIBLE);
            playlistRecyclerView.setVisibility(View.GONE);
            playlistPreparingProgressbar.setVisibility(View.GONE);

            playlistMessagePanel.setText("Add Items To Queue And Enjoy");
        } else {

            playlistMessagePanel.setVisibility(View.GONE);
            playlistAdapter.setPlaylistItem(queuedItems);
            playlistRecyclerView.setAdapter(playlistAdapter);
            playlistPreparingProgressbar.setVisibility(View.GONE);
            playlistRecyclerView.setVisibility(View.VISIBLE);

        }
    }

    private int getNextRepeatModeIndex() {

        String mode = utils.getRepeatMode();

        if (mode.equals(Constants.MODE_REPEAT_ALL)) {
            utils.setRepeatMode(Constants.MODE_SUFFLE);
            return 0;//suffle
        } else if (mode.equals(Constants.MODE_SUFFLE)) {
            utils.setRepeatMode(Constants.MODE_REPEAT_NONE);
            return 2;// no repeat
        } else {
            utils.setRepeatMode(Constants.MODE_REPEAT_ALL);
            return 1;// repeat all
        }

    }

    private String getCurrentRepeatModeBtnText() {

        String mode = utils.getRepeatMode();
        if (mode.equals(Constants.MODE_REPEAT_ALL)) {

            return repeatModesList[1];//repeat all

        } else if (mode.equals(Constants.MODE_SUFFLE)) {

            return repeatModesList[0];//suffle

        } else {

            return repeatModesList[2];//repeat none

        }
    }

    private String getCurrentRepeatModeText() {

        String mode = utils.getRepeatMode();
        if (mode.equals(Constants.MODE_REPEAT_ALL)) {

            return repeatModesTexts[1];//repeat all

        } else if (mode.equals(Constants.MODE_SUFFLE)) {

            return repeatModesTexts[0];//suffle

        } else {

            return repeatModesTexts[2];//repeat none

        }
    }

    private void transformThumbnail(float slideOffset) {

        float _dimen_DIFFpx = inPx(36);
        float _margin_DIFFpx = inPx(52);
        float _margin_top_DIFFpx = inPx(30);

        float px = inPx(64);

        int newDimen = (int) Math.ceil((slideOffset) * _dimen_DIFFpx + px);
        int newMargin = (int) Math.ceil((slideOffset) * _margin_DIFFpx);

        int newMarginTop = (int) Math.ceil((slideOffset) * _margin_top_DIFFpx);

        FrameLayout.LayoutParams thumbnailParams = new FrameLayout.LayoutParams(newDimen, newDimen);
        thumbnailParams.setMargins(newMargin, newMarginTop, 0, 0);
        thumbnail.setLayoutParams(thumbnailParams);

        playerBg.setAlpha((float) (1.0 - slideOffset));
        progressBarStream.setAlpha((float) (1.0 - slideOffset));
    }

    private void transformControl(float offset) {

        playlistBtn.setAlpha((float) (1.0 - offset * 5));
        pushDown.setAlpha((float) (offset));

        float _dimen_DIFFpx = inPx(14);
        float _margin_top_DIFFpx = inPx(36);
        float px = inPx(36);

        int newDimen = (int) Math.ceil((offset) * _dimen_DIFFpx + px);
        int newMarginTop = (int) Math.ceil((offset) * _margin_top_DIFFpx + inPx(6));

        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(newDimen, newDimen);

        controlParams.setMargins(0, newMarginTop, 20, 20);
        nextBtn.setLayoutParams(controlParams);
        pauseBtn.setLayoutParams(controlParams);

    }

    private void transformInfo(float offset) {

        int startTextSize = 28;
        int _textSize = (int) Math.ceil((offset) * 28 + startTextSize);
        pauseBtn.setTextSize(_textSize);
        nextBtn.setTextSize(_textSize);


        float alp = (float) (1.0 - offset * 8);
        float sec_alp = offset * 10;

        title.setAlpha(alp);
        artist.setAlpha(alp);
        title_second.setAlpha(sec_alp);
        artist_second.setAlpha(sec_alp);

    }

    private void transactFragment(int fragmentType, String extraa , String mode) {

        FragmentManager manager = getSupportFragmentManager();

        setTitle(extraa);

        switch (fragmentType) {

            case FRAGMENT_EXPLORE:

                ExploreFragment exploreFragment = new ExploreFragment();

                manager
                        .beginTransaction()
                        .replace(R.id.fragmentPlaceHolder, exploreFragment)
                        .commitAllowingStateLoss();


                break;

            case FRAGMENT_SHOW_ALL:

                ShowAllFragment showAllFragment = new ShowAllFragment();
                showAllFragment.setExtraa(extraa);
                manager
                        .beginTransaction()
                        .replace(R.id.fragmentPlaceHolder, showAllFragment)
                        .commitAllowingStateLoss();


                break;



            case FRAGMENT_SEARCH:

                SearchFragment searchFragment = new SearchFragment();
                searchFragment.setExtraa(extraa);
                Log.d("SearchTest"," setting mode "+mode);
                searchFragment.setSearchMode(mode);
                searchFragment.setActionListener(this);

                manager
                        .beginTransaction()
                        .replace(R.id.fragmentPlaceHolder, searchFragment)
                        .commit();


                break;

            case FRAGMENT_DOWNLOADS:

                DownloadsFragment downloadsFragmentFragment = new DownloadsFragment();

                manager
                        .beginTransaction()
                        .replace(R.id.fragmentPlaceHolder, downloadsFragmentFragment)
                        .commit();

                break;

            case FRAGMENT_SETTINGS:

                SettingsFragment settingsFragment = new SettingsFragment();

                manager
                        .beginTransaction()
                        .replace(R.id.fragmentPlaceHolder, settingsFragment)
                        .commit();

                break;

            default:
                break;
        }
    }

    private float inPx(int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private String getTimeFromMillisecond(int millis) {

        String hr;
        String min;
        String sec;
        String time;
        int i_hr = (millis / 1000) / 3600;
        int i_min = (millis / 1000) / 60;
        int i_sec = (millis / 1000) % 60;

        if (i_hr == 0) {
            min = (String.valueOf(i_min).length() < 2) ? "0" + i_min : String.valueOf(i_min);
            sec = (String.valueOf(i_sec).length() < 2) ? "0" + i_sec : String.valueOf(i_sec);
            time = min + " : " + sec;
        } else {
            hr = (String.valueOf(i_hr).length() < 2) ? "0" + i_hr : String.valueOf(i_hr);
            min = (String.valueOf(i_min).length() < 2) ? "0" + i_min : String.valueOf(i_min);
            sec = (String.valueOf(i_sec).length() < 2) ? "0" + i_sec : String.valueOf(i_sec);
            time = hr + " : " + min + " : " + sec;
        }

        // Log.d("StreamingHome"," time returned "+time);

        return time;


    }


//=============================================================================================================================================================//

    public class StreamProgressUpdateBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(Constants.ACTION_STREAM_PROGRESS_UPDATE_BROADCAST)) {

                int contentLen = Integer.parseInt(intent.getStringExtra(Constants.EXTRAA_STREAM_CONTENT_LEN));
                int buffered = Integer.parseInt(intent.getStringExtra(Constants.EXTRAA_STREAM_BUFFERED_PROGRESS));
                int progress = Integer.parseInt(intent.getStringExtra(Constants.EXTRAA_STREAM_PROGRESS));

                if (contentLen > 0) {

                    try {

                        String trackLen = getTimeFromMillisecond(contentLen);

                        if (contentLen > 0 && buffered > 0) {

                            pauseBtn.setEnabled(true);
                            nextBtn.setEnabled(true);
                            utils.setStreamContentLength(trackLen);
                            //startNotificationService();

                        }

                        pauseBtn.setEnabled(true);
                        nextBtn.setEnabled(true);
                        seekBar.setMax(contentLen);
                        progressBarStream.setMax(contentLen);

                        seekBar.setProgress(progress);
                        progressBarStream.setProgress(progress);
                        streamDuration.setText(getTimeFromMillisecond(progress) + "/" + trackLen);

                        if (mBuffered < buffered) {
                            seekBar.setSecondaryProgress(buffered);
                            mBuffered = buffered;
                        }

                    } catch (Exception e) {
                        Log.d("StreamTest", "something went wrong " + e);
                    }
                }
            }

        }
    }

    public class NotificationPlayerStateBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
//
            Log.i("NotificationPlayerState", " onReceive() action=:"+intent.getAction());
//            if (exoPlayer != null) {
//
                if (intent.getAction().equals(Constants.ACTIONS.PLAY_TO_PAUSE)) {
//                    Log.i("NotificationPlayer", " onReceive() PLAY->PAUSE");
//                    // pause
                    pauseBtn.setText(playBtnString);
                    utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_PAUSED);
//                    exoPlayer.setPlayWhenReady(false);
               }
                if (intent.getAction().equals(Constants.ACTIONS.PAUSE_TO_PLAY)) {
//                    // play
//                    Log.i("NotificationPlayer", "onReceive() PAUSE->PLAY");
                    pauseBtn.setText(pauseBtnString);
                    utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_PLAYING);
//                    exoPlayer.setPlayWhenReady(true);
                }
////
////                if (intent.getAction().equals(Constants.ACTIONS.NEXT_ACTION)) {
////                    // next
////                    Log.i("NotificationPlayer", "onReceive() NEXT");
////                    onNextRequested();
////                }
////
//                if (intent.getAction().equals(Constants.ACTIONS.STOP_PLAYER)) {
////
////                    Log.i("NotificationPlayer", "onReceive() STOP");
//                    utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_STOPPED);
////                    resetPlayer();
////
//                }
//            }
        }
    }

    public class StreamUriBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(Constants.ACTION_STREAM_URL_FETCHED)) {

                L.m("PlaylistTest", "update via broadcast: streaming uri " + intent.getStringExtra(Constants.EXTRAA_URI));

                utils.setStreamUrlFetchedStatus(true);
                String uri = intent.getStringExtra(Constants.EXTRAA_URI);
                utils.setNextStreamUrl(uri);
                utils.setStreamUrlFetcherInProgress(false);

                if (uri.equals(Constants.STREAM_PREPARE_FAILED_URL_FLAG)) {
                    promptError("Something is Wrong !! Please Try Again.");
                    return;
                }

                playNext(false);

            }

        }
    }

    private class PrepareBottomPlayerBroadcastReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            prepareBottomPlayer();
        }

    }

    private class SongActionBroadcastListener extends BroadcastReceiver {

        private final int ACTION_STREAM = 101;
        private final int ACTION_DOWNLOAD = 102;
        private final int ACTION_SHOW_ALL = 103;
        private final int ACTION_ADD_TO_QUEUE = 104;

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(Constants.ACTIONS.AUDIO_OPTIONS)) {

                int actionType = intent.getIntExtra("actionType", 0);
                String v_id = intent.getStringExtra("vid");
                String title = intent.getStringExtra("title");

                switch (actionType) {

                    case ACTION_STREAM:
                        initStream(v_id, title);
                        break;
                    case ACTION_DOWNLOAD:

                        break;
                    case ACTION_SHOW_ALL:

                        break;
                    case ACTION_ADD_TO_QUEUE:

                        break;
                    default:
                        break;

                }
            }
        }
    }

    private void registerReceivers() {

        streamProgressUpdpateReceiver = new StreamProgressUpdateBroadcastReceiver();
        streamUriReceiver = new StreamUriBroadcastReceiver();
        songActionReceiver = new SongActionBroadcastListener();
        prepareBottomPlayerReceiver = new PrepareBottomPlayerBroadcastReceiver();
        notificationPlayerStateReceiver = new NotificationPlayerStateBroadcastReceiver();

        registerReceiver(streamProgressUpdpateReceiver, new IntentFilter(Constants.ACTION_STREAM_PROGRESS_UPDATE_BROADCAST));
        registerReceiver(streamUriReceiver, new IntentFilter(Constants.ACTION_STREAM_URL_FETCHED));
        registerReceiver(songActionReceiver, new IntentFilter(Constants.ACTIONS.AUDIO_OPTIONS));
        registerReceiver(notificationPlayerStateReceiver,new IntentFilter(Constants.ACTIONS.PAUSE_TO_PLAY));
        registerReceiver(notificationPlayerStateReceiver,new IntentFilter(Constants.ACTIONS.PLAY_TO_PAUSE));
        registerReceiver(prepareBottomPlayerReceiver, new IntentFilter(Constants.ACTION_PREPARE_BOTTOM_PLAYER));
        receiverRegistered = true;

    }

    private void unRegisterReceivers() {

        unregisterReceiver(streamProgressUpdpateReceiver);
        unregisterReceiver(streamUriReceiver);
        unregisterReceiver(songActionReceiver);
        unregisterReceiver(notificationPlayerStateReceiver);
        unregisterReceiver(prepareBottomPlayerReceiver);
        receiverRegistered = false;

    }

    private void registerNotificationPlayerControlBR(){

        if(!utils.isNotificationPlayerControlReceiverRegistered()) {
            notificationPlayerStateReceiver = new NotificationPlayerStateBroadcastReceiver();
            registerReceiver(notificationPlayerStateReceiver, new IntentFilter(Constants.ACTIONS.PAUSE_TO_PLAY));
            registerReceiver(notificationPlayerStateReceiver, new IntentFilter(Constants.ACTIONS.PLAY_TO_PAUSE));
            registerReceiver(notificationPlayerStateReceiver, new IntentFilter(Constants.ACTIONS.NEXT_ACTION));
            registerReceiver(notificationPlayerStateReceiver, new IntentFilter(Constants.ACTIONS.STOP_PLAYER));
            Log.d("AnyAudio"," notification br registered");
            utils.setNotificationPlayerControlReceiverRegistered(true);
        }
    }

    private void unRegisterNotificationPlayerControlBR(){

        //notificationPlayerStateReceiver = new NotificationPlayerStateBroadcastReceiver();
        if(utils.isNotificationPlayerControlReceiverRegistered()){
            unregisterReceiver(notificationPlayerStateReceiver);
            Log.d("AnyAudio"," notification br de-registered");
            utils.setNotificationPlayerControlReceiverRegistered(false);
        }

    }

//=============================================================================================================================================================//


    private void promptError(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void initStream(String video_id, String title) {

        if (playerPlaceHolderView.getVisibility() == View.VISIBLE) {
            playerPlaceHolderView.setVisibility(View.GONE);
        }

        if (ConnectivityUtils.getInstance(this).isConnectedToNet()) {
            resetPlayer();
            utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_PLAYING);
            prepareBottomPlayer();
        } else {
            // re-init player
            prepareBottomPlayer();
        }

        StreamUrlFetcher
                .getInstance(this)
                .setBroadcastMode(true)
                .setData(video_id, title)
                .initProcess();

    }

    private void prepareBottomPlayer() {

        mLayout.setEnabled(true);
        mLayout.setClickable(true

        );

        String streamUri = utils.getLastItemThumbnail();
        String streamTitle = utils.getLastItemTitle();
        String streamArtist = utils.getLastItemArtist();

        //Player-View Common
        switch (utils.getPlayerState()) {

            case Constants.PLAYER.PLAYER_STATE_PLAYING:
                Log.d("PlayerTest", " Playing State");
                pauseBtn.setText(pauseBtnString);
                pauseBtn.setEnabled(true);
                nextBtn.setEnabled(true);
                break;

            case Constants.PLAYER.PLAYER_STATE_PAUSED:

                Log.d("PlayerTest", " Paushed State");
                pauseBtn.setText(playBtnString);
                pauseBtn.setEnabled(true);
                nextBtn.setEnabled(false);

                break;

            case Constants.PLAYER.PLAYER_STATE_STOPPED:
                Log.d("PlayerTest", " Stopped State");
                pauseBtn.setText(playBtnString);
                pauseBtn.setEnabled(true);
                nextBtn.setEnabled(false);
                break;

        }

        if (utils.getAutoPlayMode()) {

            repeatModeBtn.setVisibility(View.GONE);
            repeatModeText.setVisibility(View.GONE);
            autoplaySwitch.setChecked(true);

        } else {

            repeatModeBtn.setText(getCurrentRepeatModeBtnText());
            repeatModeBtn.setVisibility(View.VISIBLE);
            repeatModeText.setText(getCurrentRepeatModeText());
            repeatModeText.setVisibility(View.VISIBLE);
            autoplaySwitch.setChecked(false);

        }

        //thumbnail init
        Picasso.with(this).load(streamUri).transform(new CircularImageTransformer()).into(thumbnail);
        //Player-View Visible
        title.setText(streamTitle);
        artist.setText(streamArtist);
        progressBarStream.setProgress(0);
        progressBarStream.setSecondaryProgress(0);
        //Player-View Hidden
        title_second.setText(streamTitle);
        artist_second.setText(streamArtist);
        streamDuration.setText("00:00/00:00");
        seekBar.setProgress(0);
        seekBar.setSecondaryProgress(0);

        //plug adapter

        playlistAdapter = PlaylistAdapter.getInstance(this);
        playlistAdapter.setPlaylistItemListener(this);
        playlistRecyclerView.setAdapter(playlistAdapter);

        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                nextBtn.setEnabled(false);
                onNextRequested();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int position, boolean fromUser) {

                if (fromUser && anyPlayer!=null) {
                        L.m("Home", "sending seek msg");
                        if (anyPlayer.getBufferedPosition() > position)
                            anyPlayer.seekTo(position);
                }

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        pauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (anyPlayer != null) {

                    switch (utils.getPlayerState()) {
                        case Constants.PLAYER.PLAYER_STATE_STOPPED:

                            Log.i("Notification", "play tapped after stopped");
                            pauseBtn.setText(pauseBtnString);
                            initStream(utils.getLastItemStreamUrl(), utils.getLastItemTitle());
                            utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_PLAYING);

                            break;

                        case Constants.PLAYER.PLAYER_STATE_PLAYING:
                            //pause
                            utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_PAUSED);
                            pauseBtn.setText(playBtnString);
                            break;
                        case Constants.PLAYER.PLAYER_STATE_PAUSED:
                            //play
                            utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_PLAYING);
                            pauseBtn.setText(pauseBtnString);
                            break;
                        default:
                            break;

                    }

                    sendPlayerStateToStreamService(utils.getPlayerState() == Constants.PLAYER.PLAYER_STATE_PLAYING);
                    //exoPlayer.setPlayWhenReady((utils.getPlayerState() == Constants.PLAYER.PLAYER_STATE_PLAYING));

                } else {
                    Log.i("Notification", "play tapped after stopped");
                    pauseBtn.setText(pauseBtnString);
                    initStream(utils.getLastItemStreamUrl(), utils.getLastItemTitle());
                    utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_PLAYING);
                }
            }
        });

    }

    private void sendPlayerStateToStreamService(boolean streamerPlayState) {

        Intent notificationIntent = new Intent(this, AnyAudioStreamService.class);
        notificationIntent.setAction(Constants.ACTION_STREAM_TO_SERVICE_PLAY_PAUSE);
        notificationIntent.putExtra("play", streamerPlayState);
        notificationIntent.putExtra("imFromNotification",false);
        startService(notificationIntent);

    }

    private void resetPlayer() {

        collapsePlayerNotificationControl();
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            exoPlayer.stop();
            exoPlayer.release();
            L.m("StreamingHome", "Player Reset Done");

        }
        utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_STOPPED);

    }

    private void collapsePlayerNotificationControl() {

        Intent notificationIntent = new Intent(this, NotificationPlayerService.class);
        notificationIntent.setAction(Constants.ACTIONS.STOP_FOREGROUND_ACTION_BY_STREAMSHEET);
        startService(notificationIntent);
    }

    private void showDownloadDialog(final String v_id, final String stuff_title, final String thumbnailUrl, final String artist) {

        DialogInterface.OnClickListener downloaDialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:

                        if (!ConnectivityUtils.getInstance(AnyAudioActivity.this).isConnectedToNet()) {
                            Snackbar.make(homePanelTitle, "Download ! No Internet Connection ", Snackbar.LENGTH_LONG)
                                    .setAction("Connect", new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {

                                            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                                        }
                                    })
                                    .setActionTextColor(getResources().getColor(R.color.PrimaryColorDark))
                                    .show();

                        } else {
                            if (!checkForExistingFile(stuff_title)) {

                                TaskHandler
                                        .getInstance(AnyAudioActivity.this)
                                        .addTask(stuff_title, v_id, thumbnailUrl, artist);

                                Toast.makeText(AnyAudioActivity.this, " Added " + stuff_title + " To Download", Toast.LENGTH_LONG).show();

                            } else {

                                DialogInterface.OnClickListener reDownloadTaskAlertDialog = new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                        switch (which) {
                                            case DialogInterface.BUTTON_POSITIVE:

                                                TaskHandler
                                                        .getInstance(AnyAudioActivity.this)
                                                        .addTask(stuff_title, v_id, thumbnailUrl, artist);

                                                Toast.makeText(AnyAudioActivity.this, " Added " + stuff_title + " To Download", Toast.LENGTH_LONG).show();
                                                break;

                                            case DialogInterface.BUTTON_NEGATIVE:
                                                //dismiss dialog
                                                dialog.dismiss();
                                                break;
                                        }

                                    }
                                };


                                AlertDialog.Builder builderReDownloadAlert = new AlertDialog.Builder(AnyAudioActivity.this);
                                builderReDownloadAlert.setTitle("File Already Exists !!! ");
                                builderReDownloadAlert.
                                        setMessage(stuff_title)
                                        .setPositiveButton("Re-Download", reDownloadTaskAlertDialog)
                                        .setNegativeButton("Cancel", reDownloadTaskAlertDialog).show();

                            }
                        }

                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //dismiss dialog
                        dialog.dismiss();
                        break;
                }
            }
        };

        AlertDialog.Builder builderDownloadAlert = new AlertDialog.Builder(this);
        builderDownloadAlert.setTitle("Download");
        builderDownloadAlert.setMessage(stuff_title).setPositiveButton("Download", downloaDialogClickListener)
                .setNegativeButton("Cancel", downloaDialogClickListener).show();

    }

    private boolean checkForExistingFile(String fileNameToCheck) {

        File dir = new File(Constants.DOWNLOAD_FILE_DIR);
        File[] _files = dir.listFiles();

        for (File f : _files) {
            Log.d("HomeFileDuplicate", " checking " + (f.toString().substring(f.toString().lastIndexOf("/") + 1)) + " against " + fileNameToCheck);
            if ((f.toString().substring(f.toString().lastIndexOf("/") + 1)).equals(fileNameToCheck))
                return true;
        }
        return false;

    }

//======================================================================================================================================================================================//

    public class AnyAudioPlayer extends Thread {

        private static final String TAG = "AnyAudioPlayer";
        private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
        private static final int BUFFER_SEGMENT_COUNT = 256;
        private Context context;
        private AnyAudioActivity.AnyAudioPlayer mInstance;
        private MediaCodecAudioTrackRenderer audioRenderer;
        private Uri mUri;
        private Handler mUIHandler;
        private boolean PLAYER_STATE_ENDED = false;
        private boolean PLAYER_STATE_PLAYING = false;
        private int playerCurrentPositon = -1;
        private int playerContentDuration = -1;

        public AnyAudioPlayer(Context context, String uri) {
            this.context = context;
            mUri = Uri.parse(uri);
        }

        @Override
        public void run() {
            useExoplayer();
        }

        private void resetExoPlayer() {

            // check for already streaming
            if (exoPlayer != null)
                if (exoPlayer.getPlayWhenReady()) {
                    exoPlayer.stop();
                    exoPlayer.release();
                }

        }

        private void useExoplayer() {

            exoPlayer = ExoPlayer.Factory.newInstance(1);
            Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);
            String userAgent = Util.getUserAgent(context, "AnyAudio");
            DataSource dataSource = new DefaultUriDataSource(context, null, userAgent);

            ExtractorSampleSource sampleSource = new ExtractorSampleSource(
                    mUri,
                    dataSource,
                    allocator,
                    BUFFER_SEGMENT_SIZE * BUFFER_SEGMENT_COUNT);

            audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);

            exoPlayer.prepare(audioRenderer);
            exoPlayer.setPlayWhenReady(true);
            utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_PLAYING);

            //start Notification Player
            startNotificationService();

            exoPlayer.addListener(new ExoPlayer.Listener() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

                    if (playbackState == 5) // 5 - > integer code for player end state
                    {
                        unRegisterNotificationPlayerControlBR();
                        Log.d("ExoPlayer"," player ends => unregistering Notification control");
                        utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_STOPPED);
                    }
                }

                @Override
                public void onPlayWhenReadyCommitted() {

                }

                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    Log.d("ExoPlayer", "exo error setting stream state false");
                    utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_STOPPED);
                }
            });
            while (exoPlayer != null) {

                playerCurrentPositon = (int) exoPlayer.getCurrentPosition();
                playerContentDuration = (int) exoPlayer.getDuration();


                if (playerContentDuration != -1) {
                    if (playerCurrentPositon >= playerContentDuration) {
                        Log.d("PlaylistText", " releasing and stoping exoplayer");
                        exoPlayer.setPlayWhenReady(false);
                        exoPlayer.release();
                        exoPlayer.stop();
                        utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_STOPPED);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                playNext(true);  // true param is flag for refreshing the playlist items

                            }
                        });
                        break;
                    }
                }

                if (exoPlayer.getPlayWhenReady()) {

                    if (playerContentDuration != -1) {

                        if (utils.getNextStreamUrl().length() == 0 && !utils.isStreamUrlFetcherInProgress() && (playerContentDuration - playerCurrentPositon) < UP_NEXT_PREPARE_TIME_OFFSET) {

                            Log.d("PlaylistTest", " fetching Next Url");
                            fetchNextUrl();

                        }
                    }

                    broadcastStreamProgresUpdate(
                            String.valueOf(playerCurrentPositon),
                            String.valueOf(playerContentDuration),
                            String.valueOf(exoPlayer.getBufferedPosition())
                    );

                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }


        }

    }

    private void startNotificationService() {

        Intent notificationIntent = new Intent(this, NotificationPlayerService.class);
        notificationIntent.setAction(Constants.ACTIONS.START_FOREGROUND_ACTION);
        startService(notificationIntent);

    }

    public void broadcastStreamProgresUpdate(String playingAt, String contentLen, String bufferedProgress) {

        Intent intent = new Intent(Constants.ACTION_STREAM_PROGRESS_UPDATE_BROADCAST);
        intent.putExtra(Constants.EXTRAA_STREAM_PROGRESS, playingAt);
        intent.putExtra(Constants.EXTRAA_STREAM_CONTENT_LEN, contentLen);
        intent.putExtra(Constants.EXTRAA_STREAM_BUFFERED_PROGRESS, bufferedProgress);
        sendBroadcast(intent);

    }

    private void onNextRequested() {

        // send next action to stream service

        Intent nextIntent = new Intent(this,AnyAudioStreamService.class);
        nextIntent.setAction(Constants.ACTION_STREAM_TO_SERVICE_NEXT);
        startService(nextIntent);

//
//        long diff;
////        if (exoPlayer != null) {
////
////            exoPlayer.setPlayWhenReady(false);
////            exoPlayer.stop();
////            exoPlayer.release();
////            L.m("PlaylistTest", "Player Released");
//
//            utils.setPlayerState(Constants.PLAYER.PLAYER_STATE_STOPPED);
//            PlaylistItem nxtItem = null;
//
//            if (utils.getAutoPlayMode()) {
//
//                nxtItem = PlaylistGenerator.getInstance(this).getUpNext();
//                // refresh the list w.r.t top item.
//                if (nxtItem == null) {
//                    Toast.makeText(this, "No Item To Play Next.", Toast.LENGTH_LONG).show();
//                    return;
//                }
//
//                PlaylistGenerator.getInstance(this).refreshPlaylist();
//
//            } else {
//                nxtItem = QueueManager.getInstance(this).getUpNext();
//                if (nxtItem == null) {
//                    Toast.makeText(this, "No Item To Play Next.", Toast.LENGTH_LONG).show();
//                    return;
//                }
//            }
//
//            String upNextVid = nxtItem.videoId;
//            String upNextTitle = nxtItem.title;
//            String upNextThumbnailUrl = getImageUrl(nxtItem.youtubeId);
//            String upNextArtist = nxtItem.uploader;
//
//            Log.d("PlaylistTest", "nextVid:=" + upNextVid + " nextTitle:=" + upNextTitle);
//
//
//            //todo: check for usage
//            utils.setNextVId(upNextVid);
//            utils.setNextStreamTitle(upNextTitle);
//
//            diff = anyPlayer.getDuration() - anyPlayer.getCurrentPosition();
//
//            if (diff > UP_NEXT_PREPARE_TIME_OFFSET) {
//                L.m("PlaylistTest", "diff : " + diff);
//                // means stream fetcher not in progress
//                utils.setCurrentItemStreamUrl(upNextVid);
//                utils.setCurrentItemThumbnailUrl(upNextThumbnailUrl);
//                utils.setCurrentItemArtist(upNextArtist);
//                utils.setCurrentItemTitle(upNextTitle);
//                Log.d("PlaylistTest", "starting normal stream..");
//                initStream(upNextVid, upNextTitle);
//
//            } else {
//
//                // means stream fetcher is in progress or has finished
//                boolean isFetcherInProgress = utils.isStreamUrlFetcherInProgress();
//                String nextStreamUrl = utils.getNextStreamUrl();
//
//                if (nextStreamUrl.length() > 0) {
//
//                    playNext(false);
//
//                } else {
//
//                    if (!isFetcherInProgress) {
//                        // some network issue caused the url fetcher to stop its fetching task
//                        initStream(upNextVid, upNextTitle);
//
//                    } else {
//                        // no cases possible
//                    }
//                }
//            }
    }

    private void fetchNextUrl() {

        Log.d("PlaylistTest", "fetchNextUrl() - > ");
        utils.setStreamUrlFetcherInProgress(true);
        // get next play details
        PlaylistItem nxtItem = null;

        if (utils.getAutoPlayMode()) {
            nxtItem = playlistGenerator.getUpNext();
        } else {
            nxtItem = queueManager.getUpNext();
            if (nxtItem == null) {
                Toast.makeText(this, "No Item To Play Next.", Toast.LENGTH_LONG).show();
                return;
            }

        }

        String nextVid = nxtItem.videoId;
        String nextVidTitle = nxtItem.title;
        String upNextThumbnailUrl = getImageUrl(nxtItem.youtubeId);
        String upNextArtist = nxtItem.uploader;
        utils.setNextVId(nextVid);
        utils.setNextStreamTitle(nextVidTitle);
        // set data ready for notification and bottom sheets
        utils.setCurrentItemStreamUrl(nextVid);
        utils.setCurrentItemThumbnailUrl(upNextThumbnailUrl);
        utils.setCurrentItemArtist(upNextArtist);
        utils.setCurrentItemTitle(nextVidTitle);

        StreamUrlFetcher
                .getInstance(AnyAudioActivity.this)
                .setData(nextVid, nextVidTitle)
                .setBroadcastMode(false)
                .setOnStreamUriFetchedListener(new StreamUrlFetcher.OnStreamUriFetchedListener() {
                    @Override
                    public void onUriAvailable(String uri) {
                        Log.d("PlaylistTest", "pre-ready:>next uri available " + uri);
                        //this is first time stream url fetch
                        utils.setStreamUrlFetcherInProgress(false);
                        utils.setNextStreamUrl(uri);
                    }
                })
                .initProcess();

    }

    private void playNext(boolean refresh) {

        prepareBottomPlayer();
        String nextStreamUrl = utils.getNextStreamUrl();

        if (nextStreamUrl.length() > 0) {

            Intent intent =  new Intent(this, AnyAudioStreamService.class);
            intent.putExtra("uri",nextStreamUrl);
            intent.setAction(Constants.ACTION_STREAM_TO_SERVICE_START);
            startService(intent);

//            mPlayerThread = new AnyAudioPlayer(AnyAudioActivity.this, nextStreamUrl);
//            mPlayerThread.start();
            utils.setNextStreamUrl("");

            if (refresh) {
                PlaylistGenerator.getInstance(this).refreshPlaylist();
            }

        } else {
            Log.d("PlaylistNext", " No UpNext Item");
        }

    }

    private String getImageUrl(String vid) {
        //return "https://i.ytimg.com/vi/kVgKfScL5yk/hqdefault.jpg";
        return "https://i.ytimg.com/vi/" + vid + "/hqdefault.jpg";  // additional query params => ?custom=true&w=240&h=256
    }

}