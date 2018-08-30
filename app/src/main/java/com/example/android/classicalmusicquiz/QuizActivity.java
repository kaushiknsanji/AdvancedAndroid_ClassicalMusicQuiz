/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*  	http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.example.android.classicalmusicquiz;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;

public class QuizActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int CORRECT_ANSWER_DELAY_MILLIS = 1000;
    private static final String REMAINING_SONGS_KEY = "remaining_songs";
    private static final String TAG = QuizActivity.class.getSimpleName();
    private int[] mButtonIDs = {R.id.buttonA, R.id.buttonB, R.id.buttonC, R.id.buttonD};
    private ArrayList<Integer> mRemainingSampleIDs;
    private ArrayList<Integer> mQuestionSampleIDs;
    private int mAnswerSampleID;
    private int mCurrentScore;
    private int mHighScore;
    private Button[] mButtons;
    private SimpleExoPlayer mExoPlayer;
    private PlayerView mPlayerView;

    //For the MediaSession
    private MediaSessionCompat mMediaSessionCompat;
    //Cached Builder to keep the Playback state in sync with the MediaSession
    private PlaybackStateCompat.Builder mPlaybackStateBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        // Initialize the player view.
        mPlayerView = findViewById(R.id.playerView);

        boolean isNewGame = !getIntent().hasExtra(REMAINING_SONGS_KEY);

        // If it's a new game, set the current score to 0 and load all samples.
        if (isNewGame) {
            QuizUtils.setCurrentScore(this, 0);
            mRemainingSampleIDs = Sample.getAllSampleIDs(this);
            // Otherwise, get the remaining songs from the Intent.
        } else {
            mRemainingSampleIDs = getIntent().getIntegerArrayListExtra(REMAINING_SONGS_KEY);
        }

        // Get current and high scores.
        mCurrentScore = QuizUtils.getCurrentScore(this);
        mHighScore = QuizUtils.getHighScore(this);

        // Generate a question and get the correct answer.
        mQuestionSampleIDs = QuizUtils.generateQuestion(mRemainingSampleIDs);
        mAnswerSampleID = QuizUtils.getCorrectAnswerID(mQuestionSampleIDs);

        // Load the question mark as the background image until the user answers the question.
        mPlayerView.setDefaultArtwork(BitmapFactory.decodeResource
                (getResources(), R.drawable.question_mark));

        // If there is only one answer left, end the game.
        if (mQuestionSampleIDs.size() < 2) {
            QuizUtils.endGame(this);
            finish();
        }

        // Initialize the buttons with the composers names.
        mButtons = initializeButtons(mQuestionSampleIDs);

        //Initialize the Media Session
        initializeMediaSession();

        Sample answerSample = Sample.getSampleByID(this, mAnswerSampleID);

        if (answerSample == null) {
            Toast.makeText(this, getString(R.string.sample_not_found_error),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Initialize the player.
        initializePlayer(Uri.parse(answerSample.getUri()));
    }

    // COMPLETED (1): Create a method to initialize the MediaSession. It should create the MediaSessionCompat object, set the flags for external clients, set the available actions you want to support, and start the session.
    // COMPLETED (2): Create an inner class that extends MediaSessionCompat.Callbacks, and override the onPlay(), onPause(), and onSkipToPrevious() callbacks. Pass an instance of this class into the MediaSession.setCallback() method in the method you created in (1).

    /**
     * Initializes the Media Session to handle the media buttons events, transport control callbacks
     * and media controller events.
     */
    private void initializeMediaSession(){
        //Create a MediaSession
        mMediaSessionCompat = new MediaSessionCompat(this, TAG);

        //Needs to handle media button events and transport control callbacks (Play/Pause etc)
        mMediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //We do not want our app to be restarted through a media button when the app is not visible
        mMediaSessionCompat.setMediaButtonReceiver(null);

        //Set the available actions (Playback capabilities) and initial Playback state
        mPlaybackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        mMediaSessionCompat.setPlaybackState(mPlaybackStateBuilder.build());

        //Set the MediaSession Callbacks to handle events from a media controller
        mMediaSessionCompat.setCallback(mMediaSessionCallback);

        //Start the MediaSession since the activity is going to be visible
        mMediaSessionCompat.setActive(true);
    }

    /**
     * Initializes the button to the correct views, and sets the text to the composers names,
     * and set's the OnClick listener to the buttons.
     *
     * @param answerSampleIDs The IDs of the possible answers to the question.
     * @return The Array of initialized buttons.
     */
    private Button[] initializeButtons(ArrayList<Integer> answerSampleIDs) {
        Button[] buttons = new Button[mButtonIDs.length];
        for (int i = 0; i < answerSampleIDs.size(); i++) {
            Button currentButton = (Button) findViewById(mButtonIDs[i]);
            Sample currentSample = Sample.getSampleByID(this, answerSampleIDs.get(i));
            buttons[i] = currentButton;
            currentButton.setOnClickListener(this);
            if (currentSample != null) {
                currentButton.setText(currentSample.getComposer());
            }
        }
        return buttons;
    }


    /**
     * Initialize ExoPlayer.
     * @param mediaUri The URI of the sample to play.
     */
    private void initializePlayer(Uri mediaUri) {
        if (mExoPlayer == null) {
            //Create an instance of the ExoPlayer using the Default TrackSelector and LoadControl
            mExoPlayer = ExoPlayerFactory.newSimpleInstance(
                    new DefaultRenderersFactory(this),
                    new DefaultTrackSelector(),
                    new DefaultLoadControl()

            );
            //Connect the ExoPlayer View to the ExoPlayer
            mPlayerView.setPlayer(mExoPlayer);

            //Set the ExoPlayer.EventListener to this activity.
            mExoPlayer.addListener(mExoPlayerEventListener);

            //Initialize the MediaSource that loads data from the Media URI
            MediaSource mediaSource = new ExtractorMediaSource.Factory(
                    //Factory to read the media
                    new DefaultDataSourceFactory(this,
                            Util.getUserAgent(this, "ClassicalMusicQuiz")))
                    .createMediaSource(mediaUri);
            //Prepare the MediaSource
            mExoPlayer.prepare(mediaSource);
            //Start Playing when ready
            mExoPlayer.setPlayWhenReady(true);
        }
    }


    /**
     * Release ExoPlayer.
     */
    private void releasePlayer() {
        mExoPlayer.stop();
        mExoPlayer.release();
        mExoPlayer = null;
    }


    /**
     * The OnClick method for all of the answer buttons. The method uses the index of the button
     * in button array to to get the ID of the sample from the array of question IDs. It also
     * toggles the UI to show the correct answer.
     *
     * @param v The button that was clicked.
     */
    @Override
    public void onClick(View v) {

        // Show the correct answer.
        showCorrectAnswer();

        // Get the button that was pressed.
        Button pressedButton = (Button) v;

        // Get the index of the pressed button
        int userAnswerIndex = -1;
        for (int i = 0; i < mButtons.length; i++) {
            if (pressedButton.getId() == mButtonIDs[i]) {
                userAnswerIndex = i;
            }
        }

        // Get the ID of the sample that the user selected.
        int userAnswerSampleID = mQuestionSampleIDs.get(userAnswerIndex);

        // If the user is correct, increase there score and update high score.
        if (QuizUtils.userCorrect(mAnswerSampleID, userAnswerSampleID)) {
            mCurrentScore++;
            QuizUtils.setCurrentScore(this, mCurrentScore);
            if (mCurrentScore > mHighScore) {
                mHighScore = mCurrentScore;
                QuizUtils.setHighScore(this, mHighScore);
            }
        }

        // Remove the answer sample from the list of all samples, so it doesn't get asked again.
        mRemainingSampleIDs.remove(Integer.valueOf(mAnswerSampleID));

        // Wait some time so the user can see the correct answer, then go to the next question.
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mExoPlayer.stop();
                Intent nextQuestionIntent = new Intent(QuizActivity.this, QuizActivity.class);
                nextQuestionIntent.putExtra(REMAINING_SONGS_KEY, mRemainingSampleIDs);
                finish();
                startActivity(nextQuestionIntent);
            }
        }, CORRECT_ANSWER_DELAY_MILLIS);
    }

    /**
     * Disables the buttons and changes the background colors and player art to
     * show the correct answer.
     */
    private void showCorrectAnswer() {
        mPlayerView.setDefaultArtwork(Sample.getComposerArtBySampleID(this, mAnswerSampleID));
        for (int i = 0; i < mQuestionSampleIDs.size(); i++) {
            int buttonSampleID = mQuestionSampleIDs.get(i);

            mButtons[i].setEnabled(false);

            if (buttonSampleID == mAnswerSampleID) {
                mButtons[i].getBackground().setColorFilter(ContextCompat.getColor
                                (this, android.R.color.holo_green_light),
                        PorterDuff.Mode.MULTIPLY);
                mButtons[i].setTextColor(Color.WHITE);
            } else {
                mButtons[i].getBackground().setColorFilter(ContextCompat.getColor
                                (this, android.R.color.holo_red_light),
                        PorterDuff.Mode.MULTIPLY);
                mButtons[i].setTextColor(Color.WHITE);
            }
        }
    }


    /**
     * Release the player when the activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        // COMPLETED (4): When the activity is destroyed, set the MediaSession to inactive.
        super.onDestroy();
        //End the MediaSession when no longer needed
        mMediaSessionCompat.setActive(false);
        releasePlayer();
    }

    /**
     * A listener that listens to the ExoPlayer state changes.
     */
    private ExoPlayer.DefaultEventListener mExoPlayerEventListener = new Player.DefaultEventListener() {
        /**
         * Called when the value returned from either {@link Player#getPlayWhenReady()} or
         * {@link Player#getPlaybackState()} changes.
         *
         * Used to update the MediaSession PlayBackState to keep in sync.
         *
         * @param playWhenReady Whether playback will proceed when ready.
         * @param playbackState One of the {@code STATE} constants.
         */
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if(playWhenReady && playbackState == Player.STATE_READY){
                // COMPLETED (3): When ExoPlayer is playing, update the PlayBackState.
                Log.i(TAG, "onPlayerStateChanged: We are playing");
                //Build the PLAYING state on PlaybackState#Builder
                mPlaybackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, mExoPlayer.getCurrentPosition(), 1f);
            } else if(playbackState == Player.STATE_READY){
                // COMPLETED (3): When ExoPlayer is paused, update the PlayBackState.
                Log.i(TAG, "onPlayerStateChanged: We are paused");
                //Build the PAUSED state on PlaybackState#Builder
                mPlaybackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, mExoPlayer.getCurrentPosition(), 1f);
            }
            //Set the PlaybackState on MediaSession to keep it in sync
            mMediaSessionCompat.setPlaybackState(mPlaybackStateBuilder.build());
        }
    };

    /**
     * A MediaSessionCompat#Callback where all external clients control the player.
     */
    private MediaSessionCompat.Callback mMediaSessionCallback = new MediaSessionCompat.Callback() {
        /**
         * Override to handle requests to begin playback.
         */
        @Override
        public void onPlay() {
            mExoPlayer.setPlayWhenReady(true);
        }

        /**
         * Override to handle requests to pause playback.
         */
        @Override
        public void onPause() {
            mExoPlayer.setPlayWhenReady(false);
        }

        /**
         * Override to handle requests to skip to the previous media item.
         */
        @Override
        public void onSkipToPrevious() {
            mExoPlayer.seekTo(0);
        }
    };
}
