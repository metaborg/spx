package org.spoofax.interpreter.library.language.spxlang;

import java.io.IOException;

import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.core.Tools;
import org.spoofax.interpreter.library.AbstractPrimitive;
import org.spoofax.interpreter.library.ssl.SSLLibrary;
import org.spoofax.interpreter.stratego.Strategy;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoString;
import org.spoofax.interpreter.terms.IStrategoTerm;

/**
 * @author Md. Adil Akhter
 * Created On : Aug 25, 2011
 */
public class SPX_index_compilation_unit extends AbstractPrimitive {

	private static String NAME = "SPX_index_compilation_unit";

	private final SpxSemanticIndex index;

	public SPX_index_compilation_unit(SpxSemanticIndex index) {
		super(NAME, 0, 3);
		this.index = index;
	}

	/* (non-Javadoc)
	 * @see org.spoofax.interpreter.library.AbstractPrimitive#call(org.spoofax.interpreter.core.IContext, org.spoofax.interpreter.stratego.Strategy[], org.spoofax.interpreter.terms.IStrategoTerm[])
	 */
	@Override
	public boolean call(IContext env, Strategy[] svars, IStrategoTerm[] tvars) 
	{
		boolean successStatement = false;
		 
		if ( Tools.isTermString(tvars[0]) && Tools.isTermString(tvars[1]) && Tools.isTermAppl(tvars[2])) 
		{
			IStrategoString projectName = (IStrategoString)tvars[0];
			IStrategoString spxCompilationUnitPath = (IStrategoString)tvars[1];
			IStrategoAppl  compilationUnitRTree = (IStrategoAppl)tvars[2];
			
			try {
				successStatement  = index.indexCompilationUnit(projectName ,  spxCompilationUnitPath ,  compilationUnitRTree);
			} 
			catch(Exception ex)
			{
				SSLLibrary.instance(env).getIOAgent().printError("["+NAME+" Invokation failed . ] Error : "+ ex.getMessage());
			}
		}
		
		return successStatement;
	}

}
