package com.teamboid.twitter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.kennydude.awesomeprefs.NullView;

import com.handlerexploit.prime.ImageManager.LowPriorityThreadFactory;
import com.teamboid.twitter.cab.TimelineCAB;
import com.teamboid.twitter.columns.ColumnCacheManager;
import com.teamboid.twitter.columns.MentionsFragment;
import com.teamboid.twitter.listadapters.MessageConvoAdapter.DMConversation;
import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitterapi.search.Tweet;
import com.teamboid.twitterapi.status.GeoLocation;
import com.teamboid.twitterapi.status.Status;
import com.teamboid.twitterapi.user.User;
import com.teamboid.twitterapi.utilities.Utils;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * The adapter used for columns in the TimelineScreen.
 * 
 * @author Aidan Follestad
 */
public class TabsAdapter extends TaggedFragmentAdapter {

	private final Activity mContext;
	private final ActionBar mActionBar;
	public final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

	static final class TabInfo {
		private final Class<?> clss;
		private final Bundle args;
		public boolean aleadySelected;

		TabInfo(Class<?> _class, Bundle _args) {
			clss = _class;
			args = _args;
		}
	}

	/**
	 * Get the Fragment that's live RIGHT NOW! :D
	 * 
	 * @author kennydude
	 * @param pos
	 * @return
	 */
	public Fragment getLiveItem(Integer pos) {
		return mContext.getFragmentManager().findFragmentByTag("page:" + pos);
	}

	public TabsAdapter(Activity activity) {
		super(activity.getFragmentManager());
		mContext = activity;
		mActionBar = activity.getActionBar();
	}

	public void addTab(ActionBar.Tab tab, Class<?> clss, int index) {
		Bundle args = new Bundle();
		args.putInt("tab_index", index);
		TabInfo info = new TabInfo(clss, args);
		tab.setTag(info);
		mTabs.add(info);
		mActionBar.addTab(tab);
		notifyDataSetChanged();
	}
	
	boolean isHome = false;
	public void setIsHome(boolean b){ isHome = b; }

	public void addTab(ActionBar.Tab tab, Class<?> clss, int index, String query) {
		Bundle args = new Bundle();
		args.putInt("tab_index", index);
		if (query != null)
			args.putString("query", query);
		if(isHome == true)
			args.putBoolean("home", true);
		TabInfo info = new TabInfo(clss, args);
		tab.setTag(info);
		mTabs.add(info);
		mActionBar.addTab(tab);
		notifyDataSetChanged();
	}

	public void addTab(ActionBar.Tab tab, Class<?> clss, int index,
			String screenName, boolean manualRefresh) {
		Bundle args = new Bundle();
		args.putInt("tab_index", index);
		if (screenName != null)
			args.putString("screenName", screenName);
		args.putBoolean("manualRefresh", manualRefresh);
		TabInfo info = new TabInfo(clss, args);
		tab.setTag(info);
		mTabs.add(info);
		mActionBar.addTab(tab);
		notifyDataSetChanged();
	}

	public void addTab(ActionBar.Tab tab, Class<?> clss, int index,
			String listName, int listId) {
		Bundle args = new Bundle();
		args.putInt("tab_index", index);
		if (listName != null)
			args.putString("list_name", listName);
		if (listId > 0)
			args.putInt("list_id", listId);
		TabInfo info = new TabInfo(clss, args);
		tab.setTag(info);
		mTabs.add(info);
		mActionBar.addTab(tab);
		notifyDataSetChanged();
	}

	public void remove(int index) {
		mTabs.remove(index);
		mActionBar.removeTabAt(index);
		notifyDataSetChanged();
	}

	public void clear() {
		mTabs.clear();
		mActionBar.removeAllTabs();
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return mTabs.size();
	}
	
	@Override
    public float getPageWidth(int position){
    	return mContext.getResources().getInteger(R.integer.column_width); // TODO: Option
    }

	@Override
	public Fragment getItem(int position) {
		TabInfo info = mTabs.get(position);
		return Fragment.instantiate(mContext, info.clss.getName(), info.args);
	}

	public interface IBoidFragment {
		public boolean isRefreshing();
		public void onDisplay();
		public void performRefresh();
	}
	public static abstract class BoidAdapter<T> extends ArrayAdapter<T>{
		Account xAcc;
		public BoidAdapter(Context context, String id, Account acc) {
			super(context, 0);
			xAcc = acc;
		}
		public abstract long getItemId(int position);
		/**
		 * Reverse of getItemId()
		 * @param id ID returned by getItemId()
		 * @return
		 */
		public abstract int getPosition(long id);
		public Account getAccount(){ return xAcc; }
	}

	/**
	 * Basic List Fragment
	 * 
	 * 10000x more simplistic!
	 * 
	 * Basically, there is going to be less code in the actual fragments (no more copy and pasting thread work)
	 * which will make them easier to change and fix issues with.
	 * 
	 * @author kennydude
	 */
	public static abstract class BaseListFragment<T extends Serializable> extends ListFragment
			implements IBoidFragment {
		private static ExecutorService execService = Executors.newCachedThreadPool(new LowPriorityThreadFactory());
		
		Activity mContext;
		String origTitle;
		
		@Override
		public void onAttach(Activity a){
			super.onAttach(a);
			mContext = a;
		}
		
		public Activity getContext(){ return mContext; }
		
		@SuppressWarnings("unchecked")
		public BoidAdapter<T> getAdapter(){
			return (BoidAdapter<T>) getListAdapter();
		}
		
		// Abstracts
		public abstract String getColumnName();
		public abstract Status[] getSelectedStatuses();
		public abstract User[] getSelectedUsers();
		public abstract Tweet[] getSelectedTweets();
		public abstract DMConversation[] getSelectedMessages();
		public abstract void setupAdapter();
		// Fetches data from network; DO NOT THREAD!!!!!!! max_id may be -1
		public abstract T[] fetch( long maxId, long sinceId );
		
		// Overriden by profile screens/not as frequently used parts
		public boolean cacheContents(){ return true; }
		
		@SuppressWarnings("unchecked")
		public void saveCachedContents(List<T> contents){
			ColumnCacheManager.saveCache(getActivity(), getColumnName(), (List<Serializable>) contents);
		}
		
		long getCurrentTop(){
			return getAdapter().getItemId( getListView().getFirstVisiblePosition() );
		}
		
		public int getPaddingTop(){
			return 0;
		}
		
		public View getTopView(){
			return null;
		}
		
		@Override
		public void onStart() {
			super.onStart();
			if(getActivity() == null || getView() == null) return;
			
			getView().findViewById(R.id.container).setPadding(0, getPaddingTop(), 0, 0);
			/*View v = getTopView();
			if(v != null){
				((NullView)getView().findViewById(R.id.headerControl)).replace(v);
			}*/
			
			getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
				@Override
				public void onScrollStateChanged(AbsListView view, int scrollState) {
				}

				@Override
				public void onScroll(AbsListView view, int firstVisibleItem,
						int visibleItemCount, int totalItemCount) {
					if (totalItemCount > 0
							&& (firstVisibleItem + visibleItemCount) >= totalItemCount
							&& totalItemCount > visibleItemCount) {
						loadMore();
					}
					if (firstVisibleItem == 0
							&& getActivity().getActionBar().getTabCount() > 0) {
						if (!PreferenceManager.getDefaultSharedPreferences(getActivity())
								.getBoolean("enable_iconic_tabs", true)) {
							getActivity().getActionBar()
									.getTabAt(getArguments().getInt("tab_index"))
									.setText(R.string.mentions_str);
						} else {
							getActivity().getActionBar()
									.getTabAt(getArguments().getInt("tab_index"))
									.setText("");
						}
					}
				}
			});
			
			setupAdapter();
			
			execService.execute(new Runnable(){

				@SuppressWarnings("unchecked")
				@Override
				public void run() {
					if(getContext() == null || getView() == null) return;
					
					if(getAdapter() == null){
						Log.d("boid", "column " + getColumnName() + " is not working");
					}
					
					// Try and load a cached result if we have one
					final List<Serializable> contents = ColumnCacheManager.getCache(getContext(), getColumnName());
					
					origTitle = getContext().getActionBar()
							.getTabAt(
									getArguments().getInt(
											"tab_index")).getText().toString();
					if(contents != null){
						getContext().runOnUiThread(new Runnable(){

							@Override
							public void run() {
								getAdapter().addAll((Collection<? extends T>) contents);
								if(getAdapter().getFilter() != null)
									getAdapter().getFilter().filter("");
								
								getAdapter().notifyDataSetChanged();
							}
						});
					} else{
						performRefresh();
					}
				}
			});
		}
		
		public boolean isLoading;
		
		public void setLoading(boolean loading){
			isLoading = loading;
			if(getActivity() != null){
				getActivity().runOnUiThread(new Runnable(){
	
					@Override
					public void run() {
						try{ getActivity().invalidateOptionsMenu(); }
						catch(Exception e) { }
					}
					
				});
			}
		}

		@Override
		public boolean isRefreshing() {
			return isLoading;
		}
		
		public void onDisplay() {
			// todo - when swished to
		}
		
		public void showError(final String message){
			if(getActivity() == null || getView() == null) return; 
			getActivity().runOnUiThread(new Runnable(){

				@Override
				public void run() {
					final TextView tv = (TextView) (getView().findViewById(R.id.error));
					tv.setText(message);
					tv.setAlpha(1);
					tv.setVisibility(View.VISIBLE);
					tv.animate().setStartDelay(3000).setListener(new AnimatorListener(){
						@Override public void onAnimationCancel(Animator arg0) {}
						@Override public void onAnimationRepeat(Animator arg0) {}
						@Override public void onAnimationStart(Animator arg0) {}
						
						@Override public void onAnimationEnd(Animator arg0) {
							tv.setVisibility(View.GONE);
						}
						
					}).alpha(0);
				}
				
			});
		}
		
		public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
			return inflater.inflate(R.layout.list_content, container, false); 
		}

		@Override
		public void setEmptyText(CharSequence text) {
			if (getView() != null)
				((TextView)getView().findViewById(android.R.id.empty)).setText(text);
		}
		
		@Override
		public void setListShown(boolean shown) {
			View mProgressContainer = getView().findViewById(R.id.progressContainer);
			View mListContainer = getView().findViewById(R.id.listContainer);
			if (shown) {
				mProgressContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out));
				mListContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
				mProgressContainer.setVisibility(View.GONE);
				mListContainer.setVisibility(View.VISIBLE);
			} else {
				mProgressContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
				mListContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out));
				mProgressContainer.setVisibility(View.VISIBLE);
				mListContainer.setVisibility(View.GONE);
			}
		}
		
		public void loadMore(){
			if(isLoading == true) return; // No double loading for you!
			setLoading(true);
			execService.execute(new Runnable(){

				@Override
				public void run() {
					T[] t = fetch( getAdapter().getItemId( getAdapter().getCount() - 1 ), -1 );
					setLoading(false);
					if(t != null){
						getAdapter().addAll(t);
						if(getAdapter().getFilter() != null)
							getAdapter().getFilter().filter("");
						getAdapter().notifyDataSetChanged();
					}
				}
				
			});
		}
		
		public int getMaxPerLoad(){
			return 50;
		}
		
		public void performRefresh(){
			if(getView() == null) return;
			
			setLoading(true);
			execService.execute(new Runnable(){
				public void run(){
					long s = -1;
					if(getAdapter().getCount() > 0) s = getAdapter().getItemId(0);
					final T[] t = fetch(-1, s);
					setLoading(false);
					if(t != null){
						getContext().runOnUiThread(new Runnable(){

							@Override
							public void run() {
								long id = -1;
								try{
									id = getCurrentTop();
								} catch(Exception e){}
								
								if(t.length >= getMaxPerLoad()){
									getAdapter().clear();
								}
								for(T item : t){
									getAdapter().insert(item, 0);
								}
								//getAdapter().addAll(t);
								if(getAdapter().getFilter() != null)
									getAdapter().getFilter().filter("");
								getAdapter().notifyDataSetChanged();
								
								if(id != -1)
									getListView().setSelection( getAdapter().getPosition(id) );
								
								if(t.length > 0){
									// Set the tab unread count
								
									getActivity().getActionBar()
											.getTabAt(
													getArguments().getInt(
															"tab_index"))
											.setText(
													origTitle + (origTitle.isEmpty() ? "" : " ") + "("
															+ Integer
																	.toString(t.length)
															+ ")");
								}
							}
							
						});
						
						if(cacheContents()){
							ArrayList<T> y = new ArrayList<T>();
							for(int i = 0; i <= getAdapter().getCount() - 1; i++){
								y.add(getAdapter().getItem(i));
							}
							saveCachedContents(y);
						}
					}
				}
			});
		}
		
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setRetainInstance(true);
		}
		
		public void jumpTop() {
			if (getView() != null)
				getListView().setSelectionFromTop(0, 0);
		}
		
	}
	
	public static abstract class BaseLocationSpinnerFragment<T extends Serializable> extends BaseSpinnerFragment<T>{
		boolean isGettingLocation = false;
		public GeoLocation location = null;
		
		public GeoLocation getGeoLocation(){
			return location;
		}
		
		@Override
		public void onStart(){
			super.onStart();
			getLocation();
		}
		
		public void getLocation() {
			if (isGettingLocation)
				return;
			isGettingLocation = true;
			final LocationManager locationManager = (LocationManager) getActivity()
					.getSystemService(Context.LOCATION_SERVICE);
			LocationListener locationListener = new LocationListener() {
				@Override
				public void onLocationChanged(Location loc) {
					locationManager.removeUpdates(this);
					isGettingLocation = false;
					location = new GeoLocation(loc.getLatitude(),
							loc.getLongitude());
					performRefresh();
				}

				@Override
				public void onStatusChanged(String provider, int status,
						Bundle extras) {
				}

				@Override
				public void onProviderEnabled(String provider) {
				}

				@Override
				public void onProviderDisabled(String provider) {
				}
			};
			locationManager.requestLocationUpdates(
					LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
		}
		
	}
	
	// Timeline shared stuff
	public static abstract class BaseTimelineFragment extends BaseListFragment<Status>{
		public abstract String getAdapterId();
		
		@Override
		public void onListItemClick(ListView l, View v, int index, long id) {
			super.onListItemClick(l, v, index, id);
			Status tweet = (Status) getAdapter().getItem(index);
			if (tweet.isRetweet())
				tweet = tweet.getRetweetedStatus();
			getActivity().startActivity(new Intent(getActivity(), TweetViewer.class).putExtra(
					"sr_tweet", Utils.serializeObject(tweet)).addFlags(
					Intent.FLAG_ACTIVITY_CLEAR_TOP));
		}
		
		@Override
		public Status[] getSelectedStatuses() {
			if (getAdapter() == null && getView() == null) {
				Log.d("BOID CAB",
						"Adapter or view is null, getSelectedStatuses() cancelled...");
				return null;
			}
			ArrayList<Status> toReturn = new ArrayList<Status>();
			SparseBooleanArray choices = getListView().getCheckedItemPositions();
			for (int i = 0; i < choices.size(); i++) {
				if (choices.valueAt(i)) {
					toReturn.add((Status) getAdapter().getItem(choices.keyAt(i)));
				}
			}
			Log.d("BOID CAB", "getSelectedStatuses() returning " + toReturn.size()
					+ " items!");
			return toReturn.toArray(new Status[0]);
		}

		@Override
		public User[] getSelectedUsers() {
			return null;
		}

		@Override
		public Tweet[] getSelectedTweets() {
			return null;
		}

		@Override
		public DMConversation[] getSelectedMessages() {
			return null;
		}

		@Override
		public void setupAdapter(){
			getListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
			getListView().setMultiChoiceModeListener(TimelineCAB.choiceListener);
			if (AccountService.getCurrentAccount() != null) {
				setListAdapter( AccountService.getFeedAdapter(getActivity(), getAdapterId(),
									AccountService.getCurrentAccount().getId()) );
			}
		}
	}
	
	public static abstract class BaseSpinnerFragment<T extends Serializable> extends BaseListFragment<T>
			implements IBoidFragment {

		public View getTopView(){
			Spinner spin = new Spinner(getActivity());
			spin.setId(R.id.fragSpinner);
			return spin;
		}

		public Spinner getSpinner() {
			if (getView() == null)
				return null;
			return (Spinner) getView().findViewById(R.id.fragSpinner);
		}
		
	}

	public static abstract class BaseGridFragment extends Fragment implements
			IBoidFragment {

		public boolean isLoading = false;
		private boolean isShown;

		@Override
		public boolean isRefreshing() {
			return isLoading;
		}

		public abstract void performRefresh(boolean paginate);

		public abstract void reloadAdapter(boolean firstInitialize);

		public abstract void savePosition();

		public abstract void jumpTop();

		public abstract void filter();

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setRetainInstance(true);
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			return inflater.inflate(R.layout.grid_activity, container, false);
		}

		public void setListShown(boolean shown) {
			if (getView() == null)
				return;
			isShown = shown;
			getView().findViewById(android.R.id.list).setVisibility(
					(shown == true) ? View.VISIBLE : View.GONE);
			getView().findViewById(android.R.id.progress).setVisibility(
					(shown == false) ? View.VISIBLE : View.GONE);
			boolean condition = (getGridView().getAdapter() == null || getGridView()
					.getAdapter().isEmpty()) && isShown;
			getView().findViewById(android.R.id.empty).setVisibility(
					condition ? View.VISIBLE : View.GONE);
		}

		public GridView getGridView() {
			if (getView() == null)
				return null;
			return (GridView) getView().findViewById(android.R.id.list);
		}

		public void setEmptyText(String text) {
			if (getView() == null)
				return;
			((TextView) getView().findViewById(android.R.id.empty))
					.setText(text);
		}

		public void setListAdapter(ListAdapter adapt) {
			if (getView() == null)
				return;
			((ListView) getView().findViewById(android.R.id.list))
					.setAdapter(adapt);
		}
	}

}