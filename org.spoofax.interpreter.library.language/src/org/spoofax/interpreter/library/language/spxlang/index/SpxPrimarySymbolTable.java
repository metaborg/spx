package org.spoofax.interpreter.library.language.spxlang.index;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdbm.PrimaryMap;
import jdbm.RecordListener;
import jdbm.SecondaryHashMap;
import jdbm.SecondaryKeyExtractor;

import org.spoofax.interpreter.library.language.spxlang.index.data.ModuleDeclaration;
import org.spoofax.interpreter.library.language.spxlang.index.data.NamespaceUri;
import org.spoofax.interpreter.library.language.spxlang.index.data.PackageDeclaration;
import org.spoofax.interpreter.library.language.spxlang.index.data.SpxSymbol;
import org.spoofax.interpreter.library.language.spxlang.index.data.SpxSymbolKey;
import org.spoofax.interpreter.library.language.spxlang.index.data.SpxSymbolTableEntry;
import org.spoofax.interpreter.library.language.spxlang.index.data.SpxSymbolTableException;
import org.spoofax.interpreter.terms.IStrategoConstructor;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;

public class SpxPrimarySymbolTable implements INamespaceResolver , IPackageDeclarationRecordListener,IModuleDeclarationRecordListener {
	private final String SRC = this.getClass().getSimpleName();
	
	private final SpxSemanticIndexFacade _facade;
	private final PrimaryMap <NamespaceUri,INamespace> namespaces;
	private final SecondaryHashMap <IStrategoList,NamespaceUri,INamespace> namespaceByStrategoId;
	private transient INamespace _activeNamespace ;
	
	public ISpxPersistenceManager persistenceManager(){ return _facade.persistenceManager(); }
	public SpxPrimarySymbolTable (SpxSemanticIndexFacade facade) throws SecurityException, IOException{
		assert facade != null  : "SpxSemanticIndexFacade  is expected to non-null" ;

		_facade = facade;

		String tableName = facade.persistenceManager().getIndexId() + "primary_symbol_table.idx";
		
		namespaces  = facade.persistenceManager().loadHashMap(tableName + "namespaces.idx");
		namespaceByStrategoId = namespaces.secondaryHashMap(tableName+ ".namespaceByStrategoId.idx", 
				new SecondaryKeyExtractor<IStrategoList,NamespaceUri,INamespace>(){
					public IStrategoList extractSecondaryKey(NamespaceUri k,INamespace v) {
						return k.id(); 
					}
				});
	}
	
	/**
	 * Adding Global Namespace in symbol-table by default.
	 * @param facade
	 */
	public void addGlobalNamespace(SpxSemanticIndexFacade facade){
		this.defineNamespace(GlobalNamespace.createInstance(facade));
	}
	
	public void defineNamespace(INamespace namespace) {
		// if not already defined, defining this namespace
		if (!containsNamespace(namespace))
 			this.namespaces.put(namespace.namespaceUri(), namespace);
	}
	
	public NamespaceUri toNamespaceUri(IStrategoList spoofaxId) {
		NamespaceUri uri = getNamespaceUri(spoofaxId);
		if(uri == null) {
			uri = new NamespaceUri(spoofaxId);
		}
		return uri;
	}
	
	public INamespace resolveNamespace(IStrategoList id){
		if(_activeNamespace != null && _activeNamespace.namespaceUri().equalSpoofaxId(id))
			return _activeNamespace;
		
		Iterator<INamespace> resolvedNamespaces = namespaceByStrategoId.getPrimaryValues(id).iterator();
		if(resolvedNamespaces.hasNext())
			return resolvedNamespaces.next();
		else
			return null;
	}
	
	public INamespace resolveNamespace(NamespaceUri id) {
		if(_activeNamespace != null  && _activeNamespace.namespaceUri().equals(id))
			return _activeNamespace;
		
		return namespaces.get(id); 
	}
	
	public INamespace removeNamespace(IStrategoList id){
		INamespace nsToRemove  = resolveNamespace(id) ;
		
		if(nsToRemove != null){
			// Removing the internal namespace associate with the PackageNamespace
			if(nsToRemove instanceof PackageNamespace){
				NamespaceUri internalNamespaceUri = PackageNamespace.packageInternalNamespace(nsToRemove.namespaceUri(), _facade);
				this.namespaces.remove(internalNamespaceUri);	
			}
			
			persistenceManager().logMessage(SRC, "removenamespace | removing following namespace : " + nsToRemove);
			this.namespaces.remove(nsToRemove.namespaceUri());
		}
		
		return nsToRemove;
	}
	
	public NamespaceUri getNamespaceUri(IStrategoList id) {
		Iterable<NamespaceUri> uriIterator = namespaceByStrategoId.get(id);
		if(uriIterator != null)
			for( NamespaceUri uri : uriIterator)
				return uri;
		
		return null;
	}
	
	public boolean containsNamespace(IStrategoList id) { return namespaceByStrategoId.containsKey(id);}
	
	public boolean containsNamespace(NamespaceUri namespaceId) { return namespaces.containsKey(namespaceId);}
	
	public boolean containsNamespace(INamespace namespace) { return this.containsNamespace(namespace.namespaceUri());}

	public void clear(){  
		namespaces.clear();  
	}
	
	public int size() { return namespaces.size();}
	 
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() { return "SpxPrimarySymbolTable ( defined namespaces : " + namespaces.keySet() + ")"; 	}
	
	public Set<NamespaceUri> getAllNamespaces() { return namespaces.keySet() ; }

	public void defineSymbol(IStrategoList namespaceId, SpxSymbolTableEntry symTableEntry) throws SpxSymbolTableException {
		persistenceManager().logMessage(SRC, "defineSymbol | defining symbols with the following criteria :  search origin " + namespaceId +  " with Key : "+ symTableEntry.key + " Value : "+ symTableEntry.value);	
		ensureActiveNamespaceLoaded(namespaceId);
	
		_activeNamespace = _activeNamespace.define(symTableEntry, _facade); 
	}
	
	public Set<SpxSymbol> undefineSymbols(IStrategoList namespaceId, IStrategoTerm symbolId, IStrategoConstructor symbolType) throws SpxSymbolTableException {
		persistenceManager().logMessage(SRC, "undefineSymbol | undefineSymbol symbol with the following criteria :  search origin " + namespaceId +  " with Key : "+ symbolId + "of Type : "+ symbolType.getName());
			
	    ensureActiveNamespaceLoaded(namespaceId);
		Set<SpxSymbol> undefinedSymbols = _activeNamespace.undefineSymbols(
				symbolId, symbolType, _facade);
	
		persistenceManager().logMessage(SRC, "undefineSymbol | undefineSymbol Symbols : " + undefinedSymbols );
			
	    return undefinedSymbols;
	}
	
	private void ensureActiveNamespaceUnloaded(IStrategoList namespaceId){
		if(_activeNamespace.namespaceUri().equalSpoofaxId(namespaceId)){
			_activeNamespace = null;
		}
	}
	
	public void commit() {
		if(_activeNamespace != null) {
			this.namespaces.put(_activeNamespace.namespaceUri(), _activeNamespace);
		}
	}
	
	private void ensureActiveNamespaceLoaded(IStrategoList namespaceId) throws SpxSymbolTableException{
		if(_activeNamespace == null ||!_activeNamespace.namespaceUri().equalSpoofaxId(namespaceId)){
			commit(); 
			
			//Keeping a transient reference to the current/active Namespace
			//More likely that there are other symbols to be defined in the
			//current and active namespace. In that case, it will imporve 
			//performance as namespace resolving avoided by means of extra 
			//caching
			_activeNamespace = this.resolveNamespace(namespaceId);
			if(_activeNamespace ==null){
				throw new SpxSymbolTableException("Unknown namespaceId: "+ namespaceId+". Namespace can not be resolved from symbol-table") ;
			}
		}
		
	}

	public Set<SpxSymbol> resolveSymbols(IStrategoList namespaceId, IStrategoTerm symbolId, IStrategoConstructor symbolType) throws SpxSymbolTableException {
		persistenceManager().logMessage(SRC, "resolveSymbols | Resolving symbols with the following criteria :  search origin " + namespaceId +  " with Key : "+ symbolId + " of Type : "+ symbolType.getName());
		
		ensureActiveNamespaceLoaded(namespaceId);
		Set<SpxSymbol> resolvedSymbols = (Set<SpxSymbol>)_activeNamespace.resolveAll(symbolId, symbolType ,_facade);
		
		persistenceManager().logMessage(SRC, "resolveSymbols | Resolved Symbols : " + resolvedSymbols);
		return resolvedSymbols;
	}

	public SpxSymbol resolveSymbol(IStrategoList namespaceId, IStrategoTerm symbolId, IStrategoConstructor symbolType) throws SpxSymbolTableException {
		persistenceManager().logMessage(SRC, "resolveSymbol | Resolving symbol with the following criteria :  search origin " + namespaceId +  " with Key : "+ symbolId + "of Type : "+ symbolType.getName());
		
		ensureActiveNamespaceLoaded(namespaceId);
		
		SpxSymbol  resolvedSymbol = _activeNamespace.resolve(symbolId, symbolType ,_activeNamespace ,_facade);
		
		persistenceManager().logMessage(SRC, "resolveSymbol | Resolved Symbol : " + resolvedSymbol );
		
		return resolvedSymbol;
	}
	
	public INamespace newAnonymousNamespace(IStrategoList enclosingNamespaceId) throws SpxSymbolTableException{
		persistenceManager().logMessage(SRC, "newAnonymousNamespace | Inserting a Anonymous Namespace in following enclosing namespace : "  + enclosingNamespaceId);
		ensureActiveNamespaceLoaded(enclosingNamespaceId);
		
		INamespace localNamespace = LocalNamespace.createInstance(_facade, _activeNamespace); 
		this.defineNamespace(localNamespace);
	
		persistenceManager().logMessage(SRC, "newAnonymousNamespace | Folloiwng namesapce is created : "  + localNamespace);
		
		this.commit();
		_activeNamespace = localNamespace;
		
		return _activeNamespace ;
	}

	/**
	 * Destroying Namespace with following namespaceId
	 * @param enclosingNamespaceId
	 * 
	 * @return
	 * @throws SpxSymbolTableException
	 */
	public INamespace destroyNamespace(IStrategoList namespaceId) throws SpxSymbolTableException{
		persistenceManager().logMessage(SRC, "destroyNamespace | Removing the following namespace : "  + namespaceId);
		
		INamespace ns = this.removeNamespace(namespaceId);
		ensureActiveNamespaceUnloaded(namespaceId);
		persistenceManager().logMessage(SRC, "destroyNamespace | Folloiwng namesapce is removed : "  + ns);
		
		
		return ns;
	} 
	
	public RecordListener<IStrategoList, PackageDeclaration> getPackageDeclarationRecordListener() {
		return new RecordListener<IStrategoList, PackageDeclaration>(){

			public void recordInserted(IStrategoList packageID,
					PackageDeclaration value) throws IOException {
				// do nothing
				
			}

			public void recordUpdated(IStrategoList packageID,
					PackageDeclaration oldValue, PackageDeclaration newValue)
					throws IOException {
				// do nothing 
			}

			public void recordRemoved(IStrategoList packageID,
					PackageDeclaration value) throws IOException {
				
				removeNamespace(packageID) ;
			}};
	}

	public RecordListener<IStrategoList, ModuleDeclaration> getModuleDeclarationRecordListener() {
		return new RecordListener<IStrategoList, ModuleDeclaration>() {

			public void recordInserted(IStrategoList key, ModuleDeclaration value) throws IOException {
				// do nothing 
				
			}

			public void recordUpdated(IStrategoList key,  ModuleDeclaration oldValue, ModuleDeclaration newValue)
					throws IOException {
				// do nothing 
				
			}

			public void recordRemoved(IStrategoList moduleId, ModuleDeclaration value)
					throws IOException {
				removeNamespace(moduleId) ;
				
			}
			
		};
	}

	public void clearGlobalNamespce(SpxSemanticIndexFacade spxSemanticIndexFacade) {
		persistenceManager().logMessage(SRC, "clearGlobalNamespce | Remove all the entries stored currently in GlobalNamespace" );
		
		IStrategoList gnsId = GlobalNamespace.getGlobalNamespaceId(spxSemanticIndexFacade);
		INamespace gns = this.resolveNamespace(gnsId); 
		if(gns != null)
			gns.clear();
		
		this.namespaces.put(gns.namespaceUri(), gns);
		persistenceManager().logMessage(SRC, "clearGlobalNamespce | Successfully removed all the entries." );
	}
	
	/**
	 * Printing all the symbols current hashmap 
	 * 
	 * @throws IOException
	 */
	public void printSymbols(String state , String projectPath , String indexId) throws IOException{
		new File(projectPath + "/.log").mkdirs();
		FileWriter fstream = new FileWriter(projectPath + "/.log/"+indexId+"_symbols_"+Utils.now("yyyy-MM-dd HH.mm")+".txt" , true);
		BufferedWriter out = new BufferedWriter(fstream);
		out.write("---Logging [" +state+ "] state of Symbol-Table at :" + Utils.now("yyyy-MM-dd HH.mm.ss")+":----\n");
		try
		{	
			if(namespaces != null){
				for(INamespace ns : namespaces.values()){
					out.write("[" + ns +"]\n\n");
					logEntries(ns,out) ;
				}
			}
		}catch(IOException ex){ //ignore 
			
		}
		finally{out.close();}
	}

	private static  void logEntries( INamespace namespace , BufferedWriter logger) throws IOException{
		Map<SpxSymbolKey , List<SpxSymbol>> members = namespace.getMembers();
		for( SpxSymbolKey k : members.keySet()) {
			logger.write("\t"+k.toString()  + "  ----> \n");
			for( SpxSymbol s : members.get(k) ){
				logger.write( s.printSymbol());
			}
			logger.write("\n");
		}
		logger.write("\n");
	}
}