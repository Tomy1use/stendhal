package games.stendhal.server.maps.quests;

import games.stendhal.server.StendhalRPWorld;
import games.stendhal.server.StendhalRPZone;
import games.stendhal.server.StendhalRPRuleProcessor;
import games.stendhal.server.entity.item.Item;
import games.stendhal.server.entity.npc.ConversationStates;
import games.stendhal.server.entity.npc.ConversationPhrases;
import games.stendhal.server.entity.npc.SpeakerNPC;
import games.stendhal.server.entity.player.Player;
import games.stendhal.server.pathfinder.Node;
import games.stendhal.server.pathfinder.FixedPath;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Quest to buy icecream
 *
 * @author kymara
 */

public class IcecreamForAnnie extends AbstractQuest {

	// constants
	private static final String QUEST_SLOT = "icecream_for_annie";

	private static final String ZONE_NAME = "0_kalavan_city_gardens";

	protected SpeakerNPC npc;
	protected SpeakerNPC mummyNPC;

	protected StendhalRPZone zone;

	// The delay between repeating quests is 60 minutes
	private static final int REQUIRED_MINUTES = 30;
	@Override
	public void init(String name) {
		super.init(name, QUEST_SLOT);
	}

	private void createNPC() {
		npc = new SpeakerNPC("Annie Jones") {
			@Override
			protected void createPath() {
				List<Node> nodes = new LinkedList<Node>();
				nodes.add(new Node(44, 90));
				nodes.add(new Node(44, 86));
				nodes.add(new Node(42, 86));
				nodes.add(new Node(42, 90));
				setPath(new FixedPath(nodes, true));
			}

			@Override
			protected void createDialog() {
				addGreeting(null, new SpeakerNPC.ChatAction() {
					@Override
					public void fire(Player player, String text,
							 SpeakerNPC npc){
					       	if (!player.hasQuest(QUEST_SLOT)) {
						    npc.say("Hello, my name is Annie. I am five years old.");
					       	} else if (player.getQuest(QUEST_SLOT).equals("start")) {
						    if (player.isEquipped("icecream")){
							npc.say("Mummy says I mustn't talk to you any more. You're a stranger.");
							npc.setCurrentState(ConversationStates.IDLE);
						    }
						    else {  
							npc.say("Hello. I'm hungry.");
						    }
					       	} else if (player.getQuest(QUEST_SLOT).equals("mummy")){
					            if (player.isEquipped("icecream")){
						         npc.say("Yummy! Is that icecream for me?");
						         npc.setCurrentState(ConversationStates.QUESTION_1);
						    }
						    else {  
							npc.say("Hello. I'm hungry.");
						    }	   
					       	} else {//any other options? (like rejected quest slot)
						     npc.say("Hello.");
						}
					}
				});
			    add(ConversationStates.ATTENDING,
				ConversationPhrases.QUEST_MESSAGES, null,
				ConversationStates.ATTENDING, null,
				new SpeakerNPC.ChatAction() {
					@Override
					public void fire(Player player, String text, SpeakerNPC npc) {
						if (!player.hasQuest(QUEST_SLOT)
								|| player.getQuest(QUEST_SLOT).equals(
										"rejected")) {
							npc.say("I'm hungry! I'd like an icecream, please. Vanilla, with a chocolate flake. Will you get me one?");
							npc.setCurrentState(ConversationStates.QUEST_OFFERED);
						} else if (player.isQuestCompleted(QUEST_SLOT)) { // shouldn't
																			// happen
							npc.say("I'm full up now thank you!");
						} else if (player.getQuest(QUEST_SLOT).startsWith(
								"eating;")) {
							// She is still full from her previous icecream,
							// she doesn't want another yet
							String[] tokens = player.getQuest(QUEST_SLOT).split(
									";"); // this splits the time from the
											// word eating
							// tokens now is like an array with 'eating' in
							// tokens[0] and
							// the time is in tokens[1]. so we use just
							// tokens[1]

							long delay = REQUIRED_MINUTES * 60 * 1000; // minutes
																		// ->
							// milliseconds
							// timeRemaining is ''time when quest was done +
							// delay - time now''
							// if this is > 0, she's still full
							long timeRemaining = (Long.parseLong(tokens[1]) + delay)
									- System.currentTimeMillis();
							if (timeRemaining > 0L) {
								npc.say("I've had too much icecream. I feel sick.");
								return;
								// note: it is also possible to make the npc
								// say an approx time but this sounded wrong
								// with the 'at least'
							}
							// She has recovered and is ready for another
							npc.say("I hope another icecream wouldn't be greedy. Can you get me one?");
							npc.setCurrentState(ConversationStates.QUEST_OFFERED);
						} else {
							npc.say("Waaaaaaaa! Where is my icecream ....");
						}
					}
				});
		// Player agrees to get the icecream
		add(ConversationStates.QUEST_OFFERED,
				ConversationPhrases.YES_MESSAGES, null,
				ConversationStates.ATTENDING, null,
				new SpeakerNPC.ChatAction() {
					@Override
					public void fire(Player player, String text, SpeakerNPC npc) {
						npc.say("Thank you!");
						player.setQuest(QUEST_SLOT, "start");
						player.addKarma(10.0);
					}
				});
		// Player says no, they've lost karma
		add(ConversationStates.QUEST_OFFERED,
				ConversationPhrases.NO_MESSAGES, null, ConversationStates.IDLE,
				"Ok, I'll ask my mummy instead.", new SpeakerNPC.ChatAction() {
					@Override
					public void fire(Player player, String text,
							SpeakerNPC npc) {
						player.addKarma(-5.0);
						player.setQuest(QUEST_SLOT, "rejected");
					}
				});

		// Player has got icecream and spoken to mummy
		add(ConversationStates.QUESTION_1,
				ConversationPhrases.YES_MESSAGES, null,
				ConversationStates.ATTENDING, null,
				new SpeakerNPC.ChatAction() {
					@Override
					public void fire(Player player, String text, SpeakerNPC npc) {
					    if (player.drop("icecream")){
						        npc.say("Thank you EVER so much! You are very kind. Here, take this present.");
					        	player.setQuest(QUEST_SLOT, "eating;"
								+ System.currentTimeMillis());
							player.addKarma(10.0);
							player.addXP(500);
							Item item = StendhalRPWorld.get()
										.getRuleManager().getEntityManager()
										.getItem("present");
							player.equip(item, true);
					    }
					    else{
						npc.say("Hey, where's my icecream gone?!");
					    }
					}
				});
		// Player says no, they've lost karma
		add(ConversationStates.QUESTION_1,
				ConversationPhrases.NO_MESSAGES, null, ConversationStates.IDLE,
				"Waaaaaa! You're a big fat meanie.", new SpeakerNPC.ChatAction() {
					@Override
					public void fire(Player player, String text,
							SpeakerNPC engine) {
						player.addKarma(-5.0);
					}
				});
                                addOffer("I'm a little girl, I haven't anything to offer.");
                                addJob("I help my mummy.");
                                addHelp("Ask my mummy.");
				addGoodbye("Ta ta.");
			}
		};

		npc.setDescription("You see a little girl, playing in the playground.");
		npc.setEntityClass("pinkgirlnpc");
		npc.setPosition(44, 90);
		npc.initHP(100);
		zone.add(npc);
	}
	private void createMummyNPC() {
		mummyNPC = new SpeakerNPC("Mrs Jones") {
			@Override
			protected void createPath() {
			    // does not move
				setPath(null);
			}
			@Override
			protected void createDialog() {
				addGreeting(null, new SpeakerNPC.ChatAction() {
					@Override
					public void fire(Player player, String text,
							 SpeakerNPC mummyNPC){
					       	if (!player.hasQuest(QUEST_SLOT)) {
						    mummyNPC.say("Hello, nice to meet you.");
					       	} else if (player.getQuest(QUEST_SLOT).equals("start")) {
						    mummyNPC.say("Hello, I see you've met my daughter Annie. I hope she wasn't too demanding. You seem like a nice person.");
						    player.setQuest(QUEST_SLOT, "mummy");
						    
					       	} else 
						     mummyNPC.say("Hello again.");
						}
					}
				);
                                addOffer("I can't, I'm busy watching my daughter.");
                                addQuest("Nothing, thank you.");
                                addJob("I'm a mother.");
                                addHelp("I'll help if I can, but I have to watch my daughter.");
				addGoodbye("Bye for now.");
			}
		};

		mummyNPC.setDescription("You see a woman, resting on a bench.");
		mummyNPC.setEntityClass("woman_000_npc");
		mummyNPC.setPosition(53, 88);
		mummyNPC.initHP(100);
		zone.add(mummyNPC);
	}

	@Override
	public void addToWorld() {
		super.addToWorld();
		zone = StendhalRPWorld.get().getZone(ZONE_NAME);
		createNPC();
		createMummyNPC();
	}
}
