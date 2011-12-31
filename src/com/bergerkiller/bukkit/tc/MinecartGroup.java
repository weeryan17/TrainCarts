package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.actions.*;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

public class MinecartGroup extends ArrayList<MinecartMember> {
	private static final long serialVersionUID = 2;
	/*
	 * STATIC REGION
	 */
	private static HashSet<MinecartGroup> groups = new HashSet<MinecartGroup>();
	
	public static void unload(MinecartGroup group) {
		if (group == null) return;
		group.stop();
		for (MinecartMember mm : group) {
			MinecartMember.undoReplacement(mm);
		}
		group.clear();
		groups.remove(group);
	}
	public static MinecartGroup create(Entity... members) {
		return create(MinecartMember.convertAll(members));
	}
	public static MinecartGroup create(MinecartMember... members) {
		MinecartGroup g = new MinecartGroup();
		for (MinecartMember member : members) {
			if (member != null && member.isValidMember()) {
				g.add(member);
			}
		}
		if (!g.isValid()) return null;
		groups.add(g);
		return g;
	}
	
	public static MinecartGroup spawn(Location[] at, int... types) {
		if (at.length != types.length || at.length == 0) return null;
		MinecartGroup g = new MinecartGroup();
		for (int i = 0; i < types.length; i++) {
			g.add(MinecartMember.spawn(at[i], types[i]));
		}
		groups.add(g);
		return g;
	}
	public static MinecartGroup spawn(Block startblock, BlockFace direction, int... types) {
		ArrayList<Integer> typelist = new ArrayList<Integer>(types.length);
		for (int i : types) typelist.add(i);
		return spawn(startblock, direction, typelist);
	}
	public static MinecartGroup spawn(Block startblock, BlockFace direction, List<Integer> types) {
		Location[] destinations = TrackMap.walk(startblock, direction, types.size(), TrainCarts.cartDistance);
		if (types.size() != destinations.length || destinations.length == 0) return null;
		MinecartGroup g = new MinecartGroup();
		for (int i = 0; i < destinations.length; i++) {
			g.add(MinecartMember.spawn(destinations[destinations.length - i - 1], types.get(i)));
		}
		groups.add(g);
		return g;
	}
	
	public static MinecartGroup[] getGroups() {
		return groups.toArray(new MinecartGroup[0]);
	}
	public static MinecartGroup get(Entity e) {
		MinecartMember mm = MinecartMember.get(e);
		if (mm == null) return null;
		return mm.getGroup();
	}
	public static MinecartGroup get(String name) {
		for (MinecartGroup group : groups) {
			if (group.name == null) continue;
			if (group.name.equalsIgnoreCase(name)) return group;
		}
		return null;
	}
	
	public static boolean link(Minecart m1, Minecart m2) {
		if (m1 == m2) return false;
		if (m1.isDead()) return false;
		if (m2.isDead()) return false;
		return link(MinecartMember.convert(m1), MinecartMember.convert(m2));
	}
	public static boolean link(MinecartMember m1, MinecartMember m2) {
		if (m1 == null || m2 == null || m1 == m2) return false;
		MinecartGroup g1 = m1.getGroup();
		MinecartGroup g2 = m2.getGroup();
		if (m1.dead || m2.dead) return false;
		//max links per update
		if (g1 != g2) {
			if (m1.isDerailed() || m2.isDerailed()) return false;
			if (GroupManager.wasInGroup(m1.uniqueId)) return false;
			if (GroupManager.wasInGroup(m2.uniqueId)) return false;
			//Can the two groups bind?
			TrainProperties prop1 = g1.getProperties();
			TrainProperties prop2 = g2.getProperties();
			if (!prop1.allowLinking || !prop2.allowLinking) {
				return false;
			}

			//Is a powered minecart required?
			if (prop1.requirePoweredMinecart || prop2.requirePoweredMinecart) {
				if (g1.size(Material.POWERED_MINECART) == 0 && g2.size(Material.POWERED_MINECART) == 0) {
					return false;
				}
			}
			
			//Can the minecarts reach the other?
			if (!MinecartMember.isTrackConnected(m1, m2)) return true;
			
			//append group1 before or after group2?
			int m1index = g1.indexOf(m1);
			int m2index = g2.indexOf(m2);

			//Validate
			if (!g2.canConnect(m1, m2index) || !g1.canConnect(m2, m1index)) {
				return false;
			}

			//Transfer properties
			if (g1.size() > g2.size()) {
				g2.getProperties().load(g1.getProperties());
			}

			//Finally link
			if (m1index == 0 && m2index == 0) {					
				g1.reverseOrder();
				g2.addAll(0, g1);
			} else if (m1index == 0 && m2index == g2.size() - 1) {
				g2.addAll(g1);
			} else if (m1index == g1.size() - 1 && m2index == 0) {
				g2.addAll(0, g1);
			} else {
				return false;
			}

			//Clear targets and active signs
			g1.clearActiveSigns();
			g2.clearActiveSigns();

			//Re-activate the signs underneath the train
			for (MinecartMember mm : g2) {
				for (Block sign : mm.getActiveSigns()) {
					g2.setActiveSign(sign, true);
				}
			}
			
			//Correct the yaw and order
			g2.getAverageForce();
			g2.updateYaw();;
			
			g1.remove();
			m2.playLinkEffect();
			return true;
		}
		return false;
	}

	public static void rename(String oldtrainname, String newtrainname) {
		for (MinecartGroup group : groups) {
			if (group.getName().equals(oldtrainname)) {
				group.setName(newtrainname);
				return;
			}
		}
		TrainProperties.get(oldtrainname).rename(newtrainname);
	}
	
    /*
     * NON-STATIC REGION
     */
	private HashSet<Block> activeSigns = new HashSet<Block>();
	private Queue<Action> actions = new LinkedList<Action>();
	private String name;
	private TrainProperties prop = null;
	
	private MinecartGroup() {}
		
	/*
	 * Name
	 */
	public String getName() {
		if (this.name == null) {
			for (int i = groups.size(); i < Integer.MAX_VALUE; i++) {
				if (!TrainProperties.exists("train" + i)) {
					this.name = "train" + i;
					break;
				}
			}
		}
		return this.name;
	}
	public void setName(String name) {
		if (name != null) {
			if (this.name == null) {
				this.prop = TrainProperties.get(name);
			} else if (!this.name.equals(name)) {
				this.prop = TrainProperties.get(this.name).rename(name);
			}
		} else if (this.prop != null) {
			this.prop.remove();
			this.prop = null;
		}
		this.name = name;
	}
	
	/*
	 * Properties
	 */
	public TrainProperties getProperties() {
		if (this.prop == null) {
			this.prop = TrainProperties.get(this.getName());
		}
		return this.prop;
	}
	public void setProperties(TrainProperties properties) {
		if (this.prop != null) {
			this.prop.remove();
		}
		this.prop = properties;
		if (this.prop != null) {
			this.name = this.prop.getTrainName();
			this.prop.add();
		} else {
			this.name = null;
		}
	}
	public void reinitProperties() {
		this.prop = null;
	}
		
	/*
	 * Actions
	 */
	public boolean hasAction() {
		return this.actions.size() > 0;
	}
	public void clearActions() {
		this.actions.clear();
	}
	public Action removeAction() {
		return this.actions.remove();
	}
	public <T extends Action> T addAction(T action) {
		this.actions.offer(action);
		return action;
	}
	public Action getCurrentAction() {
		if (this.hasAction()) {
			return this.actions.peek();
		} else {
			return null;
		}
	}
	private void updateAction() {
		if (!this.hasAction()) return;
		if (this.actions.peek().doTick()) {
			this.actions.remove();
			this.updateAction();
		}
	}
	public GroupActionWait addActionWait(long delay) {
		return this.addAction(new GroupActionWait(this, delay));
	}
	public GroupActionWaitTill addActionWaitTill(long time) {
		return this.addAction(new GroupActionWaitTill(this, time));
	}
	public GroupActionWaitTicks addActionWaitTicks(int ticks) {
		return this.addAction(new GroupActionWaitTicks(this, ticks));
	}
	public GroupActionWaitForever addActionWaitForever() {
		return this.addAction(new GroupActionWaitForever(this));
	}
	public GroupActionSizzle addActionSizzle() {
		return this.addAction(new GroupActionSizzle(this));
	}
	public boolean isActionWait() {
		Action a = this.actions.peek();
		return a == null ? false : a instanceof ActionWait;
	}
		
	/*
	 * Signs underneath this group
	 */
	public boolean isActiveSign(Block signblock) {
		if (signblock == null) return false;
		return this.activeSigns.contains(signblock);
	}
	public boolean setActiveSign(SignActionEvent signblock, boolean active) {
		Block b = signblock.getBlock();
		if (b == null) return false;
		if (active) {
			if (this.activeSigns.add(b)) {
				SignAction.executeAll(signblock, SignActionType.GROUP_ENTER);
				return true;
			}
		} else {
			if (this.activeSigns.remove(b)) {
				SignAction.executeAll(signblock, SignActionType.GROUP_LEAVE);
				return true;
			}
		}
		return false;
	}
	public boolean setActiveSign(Block signblock, boolean active) {
		if (signblock == null) return false;
		return setActiveSign(new SignActionEvent(signblock, this), active);
	}
	public void clearActiveSigns() {
		for (Block signblock : this.activeSigns) {
			SignAction.executeAll(new SignActionEvent(signblock, this), SignActionType.GROUP_LEAVE);
		}
		this.activeSigns.clear();
	}
	
	public MinecartMember head(int index) {
		return this.get(index);
	}
	public MinecartMember head() {
		return this.head(0);
	}
	public MinecartMember tail(int index) {
		return this.get(this.size() - 1 - index);
	}
	public MinecartMember tail() {
		return this.tail(0);
	}
	public MinecartMember middle() {
		return this.get((int) Math.floor((double) size() / 2));
	}
	
	public MinecartMember[] toArray() {
		return super.toArray(new MinecartMember[0]);
	}
	
	public boolean connect(MinecartMember contained, MinecartMember with) {
		if (this.size() <= 1) {
			this.add(with);
		} else if (this.head() == contained && this.canConnect(with, 0)) {
			this.add(0, with);
		} else if (this.tail() == contained && this.canConnect(with, this.size() - 1)) {
			this.add(with);
		} else {
			return false;
		}
		return true;
	}
		
	public int indexOf(Object object) {
		MinecartMember mm = MinecartMember.get(object);
		if (mm == null) return -1;
		return super.indexOf(mm);
	}
	
	public void add(int index, MinecartMember member) {
		member.group = this;
		this.getProperties().addCart(member);
		super.add(index, member);
	}
	public boolean add(MinecartMember member) {
		member.group = this;
		this.getProperties().addCart(member);
		return super.add(member);
	}
	public boolean addAll(int index, Collection<? extends MinecartMember> members) {
		for (MinecartMember m : members) {
			m.group = this;
			this.getProperties().addCart(m);
		}
		return super.addAll(index, members);
	}
	public boolean addAll(Collection<? extends MinecartMember> members) {
		for (MinecartMember m : members) {
			m.group = this;
			this.getProperties().addCart(m);
		}
		return super.addAll(members);
	}
	
	public boolean contains(Object o) {
	    return super.contains(MinecartMember.get(o));
	}
	public boolean containsIndex(int index) {
		return this.isEmpty() ? false : index >= 0 && index < this.size();
	}
	
	public World getWorld() {
		if (this.size() == 0) return null;
		return this.get(0).getWorld();
	}
	
	public double length() {
		return TrainCarts.cartDistance * (this.size() - 1);
	}
	public int size(Material carttype) {
		switch (carttype) {
		case STORAGE_MINECART : return this.size(1);
		case POWERED_MINECART : return this.size(2);
		default : return this.size(0);
		}
	}
	public int size(int carttype) {
		int rval = 0;
		for (MinecartMember mm : this) {
			if (mm.type == carttype) rval++;
		}
		return rval;
	}
	
	public boolean isValid() {
		if (this.size() == 0) return false;
		if (this.size() == 1) return true;
		if (this.getProperties().requirePoweredMinecart) {
			return this.size(Material.POWERED_MINECART) > 0;
		} else {
			return true;
		}
	}
	
	public boolean remove(Object o) {
		int index = this.indexOf(o);
		if (index == -1) return false;
		return this.remove(index) != null;
	}
	private MinecartMember removeMember(int index) {
		MinecartMember member = super.get(index);
		//Delete the member if dead, otherwise remove active signs from this group only
		if (member.dead) {
			//added Bukkit vehicle destroy event
			member.clearActiveSigns();
		} else {
			Set<Block> toRemove = new HashSet<Block>();
			toRemove.addAll(member.getActiveSigns());
			for (MinecartMember mm : this) {
				if (mm != member) toRemove.removeAll(mm.getActiveSigns());
			}
			for (Block sign : toRemove) {
				this.setActiveSign(sign, false);
			}
		}
		super.remove(index);
		this.getProperties().removeCart(member);
		Action a;
		for (Iterator<Action> actionit = this.actions.iterator(); actionit.hasNext();) {
			a = actionit.next();
			if (a instanceof MemberAction) {
				if (((MemberAction) a).getMember() == member) {
					actionit.remove();
				}
			}
		}
		member.group = null;
		return member;
	}
	public MinecartMember remove(int index) {
		MinecartMember removed = this.removeMember(index);
		if (this.size() == 0) {
			//Remove empty group as a result
			this.remove();
		} else {
			removed.playLinkEffect();
			this.split(index);
		}
		return removed;
	}
	
	public void clear() {
		this.clearActiveSigns();
		this.clearActions();
		for (MinecartMember mm : this) {
			this.getProperties().removeCart(mm);
			if (mm.group == this) {
				mm.group = null;
				if (!mm.dead) {
					mm.getGroup().getProperties().load(this.getProperties());
				}
			}
		}
		super.clear();
	}
	
	public void remove() {
		this.clear();
		if (this.prop != null) {
			this.prop.remove();
			this.prop = null;
		}
		groups.remove(this);
	}
	public void destroy() {
		for (MinecartMember mm : this) mm.destroy(false);
		this.remove();
	}	
	public void stop() {
		for (MinecartMember m : this) {
			m.stop();
		}
	}
	public void limitSpeed() {
		for (MinecartMember mm : this) {
			mm.limitSpeed();
		}
	}
	public void eject() {
		for (MinecartMember mm : this) mm.eject();
	}
	public void eject(Vector offset) {
		for (MinecartMember mm : this) mm.eject(offset);
	}

	public void shareForce() {
		double f = this.getAverageForce();
		for (MinecartMember m : this) {
			m.setForwardForce(f);
		}
	}
	public void reverseOrder() {
		Collections.reverse(this);
	}
	public void reverse() {
		for (MinecartMember mm : this) {
			mm.reverse();
		}
		this.reverseOrder();
	}
	public void setForwardForce(double force) {
		final double currvel = this.head().getForce();
		if (currvel <= 0.01 || Math.abs(force) < 0.01) {
			for (MinecartMember mm : this) {
				mm.setForwardForce(force);
			}
		} else {
			final double f = force / currvel;
			for (MinecartMember mm : this) {
				mm.setForceFactor(f);
			}
		}
		
	}
	
	public boolean canConnect(MinecartMember mm, int at) {
		if (this.size() == 1) return true;
		if (this.size() == 0) return false;
		if (at == 0) {
			//compare the head
			return this.head().isNearOf(mm);
		} else if (at == this.size() - 1) {
			//compare the tail
			return this.tail().isNearOf(mm);
		} else {
			return false;
		}
	}
	public void updateYaw() {
		if (this.size() == 1) {
			this.get(0).updateYaw();
		} else if (this.size() > 1) {
			//Update yaw from other cart
			tail().updateYawTo(tail(1));
			for (int i = size() - 2;i >= 0;i--) {
				get(i).updateYawFrom(get(i + 1));
			}
		}
	}
	public double getAverageForce() {
		if (this.isEmpty()) return 0;
		if (this.size() == 1) return this.get(0).getForce();
		//Get the average forwarding force of all carts
		double force = 0;
		double fforce = 0;
		for (MinecartMember m : this) {
			double f = m.getForwardForce();
			fforce += f;
			if (f < 0) {
				force -= m.getForce();
			} else {
				force += m.getForce();
			}
		}
		force /= size();
		//Reverse
		if (fforce < 0) {
			reverseOrder();
		}
		return force;
	}
	public List<Integer> getTypes() {
		ArrayList<Integer> types = new ArrayList<Integer>(this.size());
		for (MinecartMember mm : this) types.add(mm.type);
		return types;
	}
	
	public boolean hasPassenger() {
		for (MinecartMember mm : this) {
			if (!mm.hasPassenger()) return false;
		}
		return true;
	}
	public boolean hasFuel() {
		for (MinecartMember mm : this) {
			if (mm.hasFuel()) return true;
		}
		return false;
	}
	public boolean hasItems() {
		for (MinecartMember mm : this) {
			if (mm.hasItems()) return true;
		}
		return false;
	}
	public boolean hasItem(ItemParser item) {
		for (MinecartMember mm : this) {
			if (mm.hasItem(item)) return true;
		}
		return false;
	}
	public boolean isMoving() {
		if (this.size() == 0) return false;
		return this.head().isMoving();
	}
	public boolean canUnload() {
		if (TrainCarts.keepChunksLoadedOnlyWhenMoving && !this.isMoving()) {
			return true;
		}
		return !this.getProperties().keepChunksLoaded;
	}
	
	public boolean isInChunk(Chunk chunk) {
		return this.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
	}
	public boolean isInChunk(World world, int cx, int cz) {
		for (MinecartMember mm : this) {
			if (mm.isInChunk(world, cx, cz)) return true;
		}
		return false;
	}
	
	/*
	 * Splits this train, the index is the first cart for the new group
	 */
	public MinecartGroup split(int at) {
		if (at <= 0) return this;
		if (at >= this.size()) return null;
		//transfer the new removed carts
		MinecartGroup gnew = new MinecartGroup();
		int count = this.size();
		for (int i = at; i < count; i++) {
			gnew.add(this.removeMember(0));
		}
		//Remove this train if now empty
		if (!this.isValid()) this.remove();
		//Remove if empty or not allowed, else add
		if (gnew.isValid()) {
			//Add the group
			groups.add(gnew);
							
			//Set the new active signs
			for (MinecartMember mm : gnew) {
				for (Block sign : mm.getActiveSigns()) {
					gnew.setActiveSign(sign, true);
				}
			}
			
			//Set the new group properties
			gnew.getProperties().load(this.getProperties());
			
			//what time do we want to prevent them from colliding too soon?
			//needs to travel 2 blocks in the meantime
			int time = (int) Util.limit(20 / gnew.head().getForce(), 20, 40);
			for (MinecartMember mm1 : gnew) {
				for (MinecartMember mm2: this) {
					mm1.ignoreCollision(mm2, time);
				}
			}
			return gnew;
		} else {
			gnew.remove();
			return null;
		}
	}
	
	public void doPhysics() {
		try {
			double totalforce = this.getAverageForce();
			double speedlimit = this.getProperties().speedLimit;
			if (totalforce > 0.4 && speedlimit > 0.4) {
				int bits = (int) Math.ceil(speedlimit / 0.4);
				for (MinecartMember mm : this) {
					mm.motX /= (double) bits;
					mm.motY /= (double) bits;
					mm.motZ /= (double) bits;
				}
				for (int i = 0; i < bits; i++) {
					while (!this.doPhysics(bits));
				}
				for (MinecartMember mm : this) {
					mm.motX *= (double) bits;
					mm.motY *= (double) bits;
					mm.motZ *= (double) bits;
					mm.maxSpeed = this.getProperties().speedLimit;
				}
			} else {
				this.doPhysics(1);
			}
		} catch (GroupUnloadedException ex) {
			//this group is gone
		} catch (Exception ex) {
			Util.log(Level.SEVERE, "Failed to perform physics on train '" + this.name + "':");
			ex.printStackTrace();
		}
	}
	private boolean doPhysics(int stepcount) throws GroupUnloadedException {
		//set max speed
		for (MinecartMember mm : this) mm.maxSpeed = this.getProperties().speedLimit / (double) stepcount;
		
		//Prevent index exceptions: remove if not a train
		if (this.size() == 1) {
			MinecartMember mm = this.head();
			//Validate this single member
			if (mm.isValidMember()) {
				this.updateAction();
				mm.preUpdate();
				mm.postUpdate(1);
				if (this.isEmpty()) return false;
				this.updateYaw();
				return true;
			} else {
				this.remove();
				throw new GroupUnloadedException();
			}
		} else if (this.isEmpty()) {
			this.remove();
			throw new GroupUnloadedException();
		}
						
		//Validate members
		for (MinecartMember mm : this) {
			if (!mm.isValidMember()) {
				this.remove(mm);
				return false;
			}
		}
					
		//pre-update
		this.updateAction();
		for (MinecartMember m : this) {
			m.preUpdate();
		}
		
		//Get the average forwarding force of all carts
		double force = this.getAverageForce();
		this.updateYaw();		
		
		//Perform forward force or not? First check if we are not messing up...
		boolean performUpdate = true;
		for (int i = 0; i < this.size() - 1; i++) {
			if (!head(i + 1).isFollowingOnTrack(head(i))) {
				performUpdate = false;
				break;
			}
		}
		
		if (performUpdate) {
			//update force
			for (MinecartMember m : this) {
				m.setForwardForce(force);
			}							
		}
		
		//Apply force factors to carts from last cart and perform post positional updates
		final int size = this.size();
		if (size < 2) return false;
		this.tail().postUpdate(1);
		if (size != this.size()) return false;
		for (int i = size - 2; i >= 0; i--) {
			double distance = get(i).distanceXZ(get(i + 1));
			double threshold = 0;
			double forcer = 1;
			if (get(i).getYawDifference(get(i + 1).getYaw()) > 10 || get(i).getPitchDifference(get(i + 1)) > 10) {
				threshold = TrainCarts.turnedCartDistance;
				forcer = TrainCarts.turnedCartDistanceForcer;
			} else {
				threshold = TrainCarts.cartDistance;
				forcer = TrainCarts.cartDistanceForcer;
			}
			if (distance < threshold) forcer *= TrainCarts.nearCartDistanceFactor;
			this.get(i).postUpdate(1 + (forcer * (threshold - distance)));
			if (size != this.size()) return false;
		}
		
		//Update order after position change
		this.getAverageForce();
		
		//update yaw and then the positions
		this.updateYaw();
		
		//Validate positions in the group
		for (int i = 0; i < this.size() - 1; i++) {
			if (!head(i + 1).isFollowingOnTrack(head(i))) {
				for (int j = i + 1; j < this.size(); j++) {
					this.get(j).setForceFactor(stepcount);
				}
				this.split(i + 1);
				return false;
			}
		}
		
		return true;
	}
		
}
