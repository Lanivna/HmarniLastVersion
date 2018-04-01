package com.google.devrel.training.conference.spi;

import static com.google.devrel.training.conference.service.OfyService.factory;

import static com.google.devrel.training.conference.service.OfyService.ofy;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Announcement;
import com.google.devrel.training.conference.domain.AppEngineUser;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ConferenceQueryForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.NotFoundException;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Work;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
//import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueFetchQueuesResponse.Queue;
import java.nio.channels.NonWritableChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import com.googlecode.objectify.cmd.Query;

/**
 * Defines conference APIs.
 */
@Api(name = "conference", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = {
        Constants.WEB_CLIENT_ID, Constants.API_EXPLORER_CLIENT_ID }, description = "API for the Conference Central Backend application.")
public class ConferenceApi {

	private static final Logger LOG = Logger.getLogger(ConferenceApi.class.getName());
	
    private static String extractDefaultDisplayNameFromEmail(String email) {
        return email == null ? null : email.substring(0, email.indexOf("@"));
    }

    // Declare this method as a method available externally through Endpoints
    @ApiMethod(name = "saveProfile", path = "profile", httpMethod = HttpMethod.POST)
    // The request that invokes this method should provide data that
    // conforms to the fields defined in ProfileForm
    // TODO 1 Pass the ProfileForm parameter
    // TODO 2 Pass the User parameter
    public Profile saveProfile(final User user, ProfileForm profileForm)
            throws UnauthorizedException {

        // TODO 2
        // If the user is not logged in, throw an UnauthorizedException
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // TODO 2
        // Get the userId and mainEmail
       /* String mainEmail = user.getEmail();
        String userId = user.getUserId();*/

        // TODO 1
        // Get the displayName and teeShirtSize sent by the request.

        String displayName = profileForm.getDisplayName();
        TeeShirtSize teeShirtSize = profileForm.getTeeShirtSize();

        // Get the Profile from the datastore if it exists
        // otherwise create a new one
        Profile profile = ofy().load().key(Key.create(Profile.class, getUserId(user)))
                .now();

        if (profile == null) {
            // Populate the displayName and teeShirtSize with default values
            // if not sent in the request
            if (displayName == null) {
                displayName = extractDefaultDisplayNameFromEmail(user
                        .getEmail());
            }
            if (teeShirtSize == null) {
                teeShirtSize = TeeShirtSize.NOT_SPECIFIED;
            }
            // Now create a new Profile entity
            profile = new Profile(getUserId(user), displayName, user.getEmail(), teeShirtSize);
        } else {
            // The Profile entity already exists
            // Update the Profile entity
            profile.update(displayName, teeShirtSize);
        }

        // TODO 3
        // Save the entity in the datastore
        ofy().save().entity(profile).now();

        // Return the profile
        return profile;
    }

    /**
     * Returns a Profile object associated with the given user object. The cloud
     * endpoints system automatically inject the User object.
     *
     * @param user
     *            A User object injected by the cloud endpoints.
     * @return Profile object.
     * @throws UnauthorizedException
     *             when the User object is null.
     */
    @ApiMethod(name = "getProfile", path = "profile", httpMethod = HttpMethod.GET)
    public Profile getProfile(final User user) throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // TODO
        // load the Profile Entity
       /* String userId = user.getUserId();
        Key key = Key.create(Profile.class, userId);

        Profile profile = (Profile) ofy().load().key(key).now();
        return profile;*/
        
        return ofy().load().key(Key.create(Profile.class, getUserId(user))).now();
    }
    
    
   
    private static Profile getProfileFromUser(User user, String userId) {
        // First fetch the user's Profile from the datastore.
        Profile profile = ofy().load().key(
                Key.create(Profile.class, user.getUserId())).now();
        if (profile == null) {
            // Create a new Profile if it doesn't exist.
            // Use default displayName and teeShirtSize
            String email = user.getEmail();
            profile = new Profile(user.getUserId(),
                    extractDefaultDisplayNameFromEmail(email), email, TeeShirtSize.NOT_SPECIFIED);
        }
        return profile;
    }
    
    private static String getUserId(User user) {
    	String userId = user.getUserId();
    	if (userId == null) {
    		LOG.info("userId is null, so trying to obtain it from the datastore.");
    		AppEngineUser appEngineUser = new AppEngineUser(user);
    		ofy().save().entity(appEngineUser).now();
    		Objectify objectify = ofy().factory().begin();
    		AppEngineUser savedUser = objectify.load().key(appEngineUser.getKey()).now();
    		userId = savedUser.getUser().getUserId();
    		LOG.info("Obtained the UserId: " + userId);
    	}
    	return userId;
    }

/**
     * Creates a new Conference object and stores it to the datastore.
     *
     * @param user A user who invokes this method, null when the user is not signed in.
     * @param conferenceForm A ConferenceForm object representing user's inputs.
     * @return A newly created Conference Object.
     * @throws UnauthorizedException when the user is not signed in.
     */
    @ApiMethod(name = "createConference", path = "conference", httpMethod = HttpMethod.POST)
    public Conference createConference(final User user, final ConferenceForm conferenceForm)
        throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        
        

        // TODO (Lesson 4)
        // Get the userId of the logged in User

        // TODO (Lesson 4)
        // Get the key for the User's Profile
        Key<Profile> profileKey = Key.create(Profile.class, getUserId(user));
        

        // TODO (Lesson 4)
        // Allocate a key for the conference -- let App Engine allocate the ID
        // Don't forget to include the parent Profile in the allocated ID
        final Key<Conference> conferenceKey = factory().allocateId(profileKey, Conference.class);

        // TODO (Lesson 4)
        // Get the Conference Id from the Key
        final long conferenceId = conferenceKey.getId();
        
        final Queue queue = QueueFactory.getDefaultQueue();
        
        final String userId = getUserId(user);

        // TODO (Lesson 4)
        // Get the existing Profile entity for the current user if there is one
        // Otherwise create a new Profile entity with default values
       
        Conference conference = ofy().transact(new Work<Conference>() {
        	@Override
        	public Conference run() {
                Profile profile = getProfileFromUser(user, userId);
                Conference conference = new Conference(conferenceId, userId, conferenceForm);
                ofy().save().entities(conference, profile).now();
                queue.add(ofy().getTransaction(),
                		TaskOptions.Builder.withUrl("/tasks/send_confirmation_email")
                		.param("email", profile.getMainEmail())
                		.param("conferenceInfo", conference.toString()));
        	return conference;
        	}
        });
        return conference;
    }
        // TODO (Lesson 4)
        // Create a new Conference Entity, specifying the user's Profile entity
        // as the parent of the conference
        //Conference conference = new Conference(conferenceId, userId, conferenceForm);

        // TODO (Lesson 4)
        // Save Conference and Profile Entities
         //ofy().save().entities(conference, profile).now();
    
    @ApiMethod(
    		name = "updateConference",
    		path = "conference/{websafeConferenceKey}",
    		httpMethod = HttpMethod.PUT
    		)
    public Conference updateConference(final User user, final ConferenceForm conferenceForm,
    		@Named("websafeConferenceKey")
    final String websafeConferenceKey)
    throws UnauthorizedException, NotFoundException, ForbiddenException, ConflictException {
    	if (user == null) {
    		throw new UnauthorizedException("Authorization required");
    	}
    	final String userId = getUserId(user);
    	
    	TxResult<Conference> result = ofy().transact(new Work<TxResult<Conference>>() {
    		@Override
    		public TxResult<Conference> run() {
    			Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
    			Conference conference = ofy().load().key(conferenceKey).now();
    			if(conference == null) {
    				return new TxResult<>(
    					new NotFoundException());
    				}
    				Profile profile = ofy().load().key(Key.create(Profile.class, userId)).now();
    				if (profile == null ||
    						!conference.getOrganizerUserId().equals(userId)) {
    					return new TxResult<>(
    							new ForbiddenException("Only the owner can update the conference."));
    		}
    				conference.updateWithConferenceForm(conferenceForm);
    				ofy().save().entity(conference).now();
    				return new TxResult<>(conference);
    		}
    	});
    	return result.getResult();
    }

    @ApiMethod(
    		name = "getAnnouncement",
    		path = "announcement",
    		httpMethod = HttpMethod.GET
    		)
    public Announcement getAnnouncement() {
    	MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
    	Object message = memcacheService.get(Constants.MEMCACHE_ANNOUNCEMENTS_KEY);
    	if(message != null) {
    		return new Announcement(message.toString());
    	}
    	return null;
    }

    @ApiMethod(
            name = "queryConferences",
            path = "queryConferences",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> queryConferences(ConferenceQueryForm conferenceQueryForm) {
        Iterable<Conference> conferenceIterable = conferenceQueryForm.getQuery();
        List<Conference> result = new ArrayList<>(0);
        List<Key<Profile>> organizersKeyList = new ArrayList<>(0);
        for (Conference conference : conferenceIterable) {
            organizersKeyList.add(Key.create(Profile.class, conference.getOrganizerUserId()));
            result.add(conference);
        }
        // To avoid separate datastore gets for each Conference, pre-fetch the Profiles.
        ofy().load().keys(organizersKeyList);
        return result;
    }
    
    @ApiMethod(
            name = "getConferencesCreated",
            path = "getConferencesCreated",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> getConferencesCreated(final User user) 
    		throws UnauthorizedException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        String userId = user.getUserId();
        Key userKey = Key.create(Profile.class,userId);
        return ofy().load().type(Conference.class)
                .ancestor(userKey)
                .order("name").list();
    }
    
    @ApiMethod(
            name = "getConferencesFiltered",
            path = "getConferencesFiltered",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> getConferencesFiltered(){
                
    Query query = ofy().load().type(Conference.class);
    query = query.filter("city =", "London");
    query = query.filter("topics =", "Web Technologies");
        return query.list();
    }
    
    public List<Conference> filterPlayground() {
        Query<Conference> query = ofy().load().type(Conference.class).order("name");

        // Filter on city
        query = query.filter("city =", "London");

        
        // Add a filter for topic = "Medical Innovations"
        query = query.filter("topics =", "Medical Innovations");

        // Add a filter for maxAttendees
        query = query.filter("maxAttendees >", 8);
        query = query.filter("maxAttendees <", 10).order("maxAttendees").order("name");

        // Add a filter for month {unindexed composite query}
        // Find conferences in June
        query = query.filter("month =", 6);

        // multiple sort orders
        query = query.filter("city =", "Tokyo").filter("seatsAvailable <", 10).
                filter("seatsAvailable >" , 0).order("seatsAvailable").order("name").
                order("month");
        

        return query.list();
    }
    
    /**
     * Returns a Conference object with the given conferenceId.
     *
     * @param websafeConferenceKey The String representation of the Conference Key.
     * @return a Conference object with the given conferenceId.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "getConference",
            path = "conference/{websafeConferenceKey}",
            httpMethod = HttpMethod.GET
    )
    public Conference getConference(
            @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws NotFoundException {
        Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
        Conference conference = ofy().load().key(conferenceKey).now();
        if (conference == null) {
            throw new NotFoundException();
        }
        return conference;
    }


 /**
     * Just a wrapper for Boolean.
     * We need this wrapped Boolean because endpoints functions must return
     * an object instance, they can't return a Type class such as
     * String or Integer or Boolean
     */
    public static class WrappedBoolean {

        private final Boolean result;
        //private final String reason;

        public WrappedBoolean(Boolean result) {
            this.result = result;
            //this.reason = "";
        }

        public WrappedBoolean(Boolean result, String reason) {
            this.result = result;
           // this.reason = reason;
        }

        public Boolean getResult() {
            return result;
        }

       /* public String getReason() {
            return reason;
        }*/
    }
    
    private static class TxResult<ResultType> {
    	
    	private ResultType result;
    	
    	private Throwable exception;
    	
    	private TxResult(ResultType result) {
    		this.result = result;
    	}
    	
    	private TxResult(Throwable exception) {
    		if (exception instanceof NotFoundException ||
    				exception instanceof ForbiddenException ||
    				exception instanceof ConflictException) {
    			this.exception = exception;
    		} else {
    			throw new IllegalArgumentException("Exception not supported.");
    		}
    	}
    	
    	private ResultType getResult() throws NotFoundException, ForbiddenException, ConflictException {
    		if (exception instanceof NotFoundException) {
    			throw (NotFoundException) exception;
    		}
    		if (exception instanceof ForbiddenException) {
    			throw (ForbiddenException) exception;
    		}
    		if (exception instanceof ConflictException) {
    			throw (ConflictException) exception;
    		}
    		return result;
    	}
    }


    @ApiMethod(
            name = "registerForConference",
            path = "conference/{websafeConferenceKey}/registration",
            httpMethod = HttpMethod.POST
    )

    public WrappedBoolean registerForConference(final User user,
            @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException,
            ForbiddenException, ConflictException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Get the userId
        final String userId = user.getUserId();
        TxResult<Boolean> result = ofy().transact(new Work<TxResult<Boolean>>() {
        	@Override
        	public TxResult<Boolean> run() {
        		Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
        		Conference conference = ofy().load().key(conferenceKey).now();
        		if (conference == null) {
        			return new TxResult<>(new NotFoundException());
        
        		}
        		Profile profile = getProfileFromUser(user, userId);
        		if(profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
        			return new TxResult<>(new ConflictException("You have already registered for this conference"));
        			
        		} else if (conference.getSeatsAvailable() <= 0) {
        			return new TxResult<>(new ConflictException("There are no seats available"));
        			
        		} else {
        			profile.addToConferenceKeysToAttend(websafeConferenceKey);
        			conference.bookSeats(1);
        			ofy().save().entities(profile, conference).now();
        			return new TxResult<>(true);
        		}
        	}
        });
        return new WrappedBoolean(result.getResult());
    }
    /*
        // TODO
        // Start transaction
        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
        	@Override
        	public WrappedBoolean run() { 
                try {

                // TODO
                // Get the conference key -- you can get it from websafeConferenceKey
                // Will throw ForbiddenException if the key cannot be created
                Key<Conference> conferenceKey = Key.create(websafeConferenceKey);

                // TODO
                // Get the Conference entity from the datastore
                Conference conference = ofy().load().key(conferenceKey).now();

                // 404 when there is no Conference with the given conferenceId.
                if (conference == null) {
                    return new WrappedBoolean (false,
                            "No Conference found with key: "
                                    + websafeConferenceKey);
                }

                // TODO
                // Get the user's Profile entity
                Profile profile = getProfileFromUser(user);

                // Has the user already registered to attend this conference?
                if (profile.getConferenceKeysToAttend().contains(
                        websafeConferenceKey)) {
                    return new WrappedBoolean (false, "Already registered");
                } else if (conference.getSeatsAvailable() <= 0) {
                    return new WrappedBoolean (false, "No seats available");
                } else {
                    // All looks good, go ahead and book the seat
                    // TODO
                    // Add the websafeConferenceKey to the profile's
                    // conferencesToAttend property
                    profile.addToConferenceKeysToAttend(websafeConferenceKey);
                    
                    // TODO 
                    // Decrease the conference's seatsAvailable
                    // You can use the bookSeats() method on Conference
                    conference.bookSeats(1);
                    // TODO
                    // Save the Conference and Profile entities
                    ofy().save().entities(profile, conference).now();
                    // We are booked!
                    return new WrappedBoolean(true, "Registration successful");
                }

                }
                catch (Exception e) {
                    return new WrappedBoolean(false, "Unknown exception");
                }
            }
        });
        // if result is false
        if (!result.getResult()) {
            if (result.getReason().contains("No Conference found with key")) {
                throw new NotFoundException ();
            }
            else if (result.getReason() == "Already registered") {
                throw new ConflictException("You have already registered");
            }
            else if (result.getReason() == "No seats available") {
                throw new ConflictException("There are no seats available");
            }
            else {
                throw new ForbiddenException("Unknown exception");
            }
        }
        return result;
    }*/


 /**
     * Returns a collection of Conference Object that the user is going to attend.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @return a Collection of Conferences that the user is going to attend.
     * @throws UnauthorizedException when the User object is null.
     */
    @ApiMethod(
            name = "getConferencesToAttend",
            path = "getConferencesToAttend",
            httpMethod = HttpMethod.GET
    )
    public Collection<Conference> getConferencesToAttend(final User user)
            throws UnauthorizedException, NotFoundException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        // TODO
        // Get the Profile entity for the user
        Profile profile = ofy().load().key(Key.create(Profile.class, user.getUserId())).now();
        if (profile == null) {
            throw new NotFoundException();
        }

        // TODO
        // Get the value of the profile's conferenceKeysToAttend property
        List<String> keyStringsToAttend = profile.getConferenceKeysToAttend();
        List<Key<Conference>> keysToAttend = new ArrayList<>();
        for (String keyString : keyStringsToAttend) {
        	keysToAttend.add(Key.<Conference>create(keyString));
        }
        return ofy().load().keys(keysToAttend).values();

        // TODO
        // Iterate over keyStringsToAttend,
        // and return a Collection of the
        // Conference entities that the user has registered to atend

        
    }
    
    /**
     * Unregister from the specified Conference.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @param websafeConferenceKey The String representation of the Conference Key 
     * to unregister from.
     * @return Boolean true when success, otherwise false.
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "unregisterFromConference",
            path = "conference/{websafeConferenceKey}/registration",
            httpMethod = HttpMethod.DELETE
    )
public WrappedBoolean unregisterFromConference(final User user,
            @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException,
            ForbiddenException, ConflictException { 
    	if (user == null) {
    		throw new UnauthorizedException("Authorization required");
    		
    	}
    	final String userId = getUserId(user);
    	TxResult<Boolean> result = ofy().transact(new Work<TxResult<Boolean>>() {
    		@Override
    		public TxResult<Boolean> run() {
    			Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
    			Conference conference = ofy().load().key(conferenceKey).now();
    			if(conference == null) {
    				return new TxResult<>(new NotFoundException());
    			}
    			Profile profile = getProfileFromUser(user, userId);
    			if(profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
    				profile.unregisterFromConference(websafeConferenceKey);
    				conference.giveBackSeats(1);
    				ofy().save().entities(profile, conference).now();
    				return new TxResult<>(true);
    			} else {
    				return new TxResult<>(false);
    			}
    		}
    	});
    	return new WrappedBoolean(result.getResult());
    }

    
}

