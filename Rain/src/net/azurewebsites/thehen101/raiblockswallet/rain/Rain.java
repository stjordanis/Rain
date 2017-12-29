package net.azurewebsites.thehen101.raiblockswallet.rain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

import net.azurewebsites.thehen101.raiblockswallet.rain.account.Account;
import net.azurewebsites.thehen101.raiblockswallet.rain.account.Address;
import net.azurewebsites.thehen101.raiblockswallet.rain.server.RequestWithHeader;
import net.azurewebsites.thehen101.raiblockswallet.rain.server.ServerConnection;
import net.azurewebsites.thehen101.raiblockswallet.rain.server.ServerManager;
import net.azurewebsites.thehen101.raiblockswallet.rain.server.listener.ListenerNewBlock;
import net.azurewebsites.thehen101.raiblockswallet.rain.server.listener.ListenerServerResponse;
import net.azurewebsites.thehen101.raiblockswallet.rain.util.POWFinder;

public final class Rain {
	private final ArrayList<byte[]> recentBlockHashes;
	private final ArrayList<Account> accounts;
	private final ServerManager serverManager;
	private final ListenerNewBlock newBlockListener;
	private final POWFinder powfinder;
	
	private MessageDigest md;
	
	public Rain(ArrayList<ServerConnection> serverConnections, ArrayList<Account> accounts, int powThreadCount) {
		this.serverManager = new ServerManager(serverConnections);
		this.recentBlockHashes = new ArrayList<byte[]>();
		for (ServerConnection connection : this.serverManager.getConnections())
			connection.start();
		try {
			this.md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		this.accounts = accounts;
		this.powfinder = new POWFinder(powThreadCount);
		this.newBlockListener = new ListenerNewBlock() {
			@Override
			public void onNewBlock(RequestWithHeader newBlockNotification) {
				notifyNewBlock(new String(newBlockNotification.getRequestBytes()));
			}
		};
		System.out.println("Rain instance initialised");
	}
	
	public String getPreviousHash(final Address address) {
		String[] previousHash = new String[1];
		previousHash[0] = null;
		String body = 
				"{" + 
					"\"action\": \"frontiers\"," + 
					"\"account\": \"" + address.getAddress() + "\"," + 
					"\"count\": \"1\"" + 
				"}";
		RequestWithHeader request = new RequestWithHeader(false, body);
		ListenerServerResponse listener = new ListenerServerResponse() {
			@Override
			public void onResponse(RequestWithHeader initialRequest, RequestWithHeader receivedRequest) {
				if (Arrays.equals(request.getRequestBytes(), initialRequest.getRequestBytes())) {
					//we got the request
					String returned = new String(receivedRequest.getRequestBytes());
					String a = returned.substring(returned.indexOf("xrb_"));
					String add = a.substring(0, a.indexOf("\""));
					if (!add.equals(address.getAddress())) {
						System.out.println(address.getAddress() + " needs to be opened.");
						previousHash[0] = "";
					} else {
						String d = returned.substring(0, returned.lastIndexOf("\""));
						int index = d.lastIndexOf("\"") + 1;
						String hash = d.substring(index);
						previousHash[0] = hash;
					}
				}
			}
		};
		this.serverManager.addListenerToAll(listener);
		this.serverManager.addToConnectedServerQueue(request);
		while (previousHash[0] == null) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		this.serverManager.removeListenerFromAll(listener);
		return previousHash[0];
	}
	
	public void notifyNewBlock(String newBlock) {
		newBlock = newBlock.replaceAll("\\s+", "").trim();
		byte[] digest = null;
		try {
			digest = md.digest(newBlock.getBytes("UTF-8"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (int i = 0; i < this.recentBlockHashes.size(); i++) {
			byte[] array = this.recentBlockHashes.get(i);
			if (Arrays.equals(digest, array))
				return;
		}
		
		int z = newBlock.indexOf("\\\"type\\\"") + 8;
		String x = newBlock.substring(z);
		int y = x.indexOf("\"") + 1;
		String w = x.substring(y);
		int v = w.indexOf("\\");
		String type = w.substring(0, v);
		if (!type.equals("send"))
			return;
		
		int a = newBlock.indexOf("\"account\"") + 9;
		String b = newBlock.substring(a);
		int c = b.indexOf("\"") + 1;
		String d = b.substring(c);
		int e = d.indexOf("\"");
		String account = d.substring(0, e);
		
		Address add = this.doesAddressStringBelongToUs(account);
		if (add == null)
			return;
		
		System.out.println("Address belongs to us: " + account);
		
		int aa = newBlock.indexOf("\"hash\"") + 6;
		String ba = newBlock.substring(aa);
		int ca = ba.indexOf("\"") + 1;
		String da = ba.substring(ca);
		int ea = da.indexOf("\"");
		String hash = da.substring(0, ea);
		System.out.println("Adding block for above address: " + hash);
		
		this.recentBlockHashes.add(digest);
		if (this.recentBlockHashes.size() > 10000)
			this.recentBlockHashes.remove(0);
		
		add.getUnpocketedTransactions().add(hash);
	}
	
	private Address doesAddressStringBelongToUs(String xrbAddress) {
		for (Account account : this.accounts) {
			int max = account.getNextAddressIndex();
			for (int i = 0; i < max; i++) {
				Address address = account.getAddressForIndex(i);
				if (address.getAddress().equals(xrbAddress))
					return address;
			}
		}
		return null;
	}
	
	public ServerManager getServerManager() {
		return this.serverManager;
	}
	
	public ArrayList<Account> getAccounts() {
		return this.accounts;
	}
	
	public ListenerNewBlock getBlockListener() {
		return this.newBlockListener;
	}
}
