package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.dom.SelectionAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateTextFileChange;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.refactoring.util.WorkingCopyUtil;
import org.eclipse.jdt.internal.corext.template.Template;
import org.eclipse.jdt.internal.corext.template.Templates;
import org.eclipse.jdt.internal.corext.template.java.JavaContext;
import org.eclipse.jdt.internal.corext.textmanipulation.SimpleTextEdit;

public class ExtractInterfaceRefactoring extends Refactoring {

	private final CodeGenerationSettings fCodeGenerationSettings;

	private IType fInputClass;
	private String fNewInterfaceName;
	private IMember[] fExtractedMembers;
	private boolean fReplaceOccurrences;
	private TextChangeManager fChangeManager;
	
	public ExtractInterfaceRefactoring(IType clazz, CodeGenerationSettings codeGenerationSettings){
		Assert.isNotNull(clazz);
		Assert.isNotNull(codeGenerationSettings);
		fInputClass= clazz;
		fCodeGenerationSettings= codeGenerationSettings;
	}
	
	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.Refactoring#checkActivation(IProgressMonitor)
	 */
	public RefactoringStatus checkActivation(IProgressMonitor pm) throws JavaModelException {
		return new RefactoringStatus();
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.Refactoring#checkInput(IProgressMonitor)
	 */
	public RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException {
		pm.beginTask("", 1);//$NON-NLS-1$
		try{
			RefactoringStatus result= new RefactoringStatus();		
			fChangeManager= createChangeManager(new SubProgressMonitor(pm, 1));
			result.merge(validateModifiesFiles());
			return result;
		} catch (CoreException e){
			throw new JavaModelException(e);
		} finally {
			pm.done();
		}	
	}
	
	public RefactoringStatus checkNewInterfaceName(String newName){
		RefactoringStatus result= Checks.checkTypeName(newName);
		return result;
	}

	private IFile[] getAllFilesToModify() throws CoreException{
		return ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits());
	}
	
	private RefactoringStatus validateModifiesFiles() throws CoreException{
		return Checks.validateModifiesFiles(getAllFilesToModify());
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.IRefactoring#createChange(IProgressMonitor)
	 */
	public IChange createChange(IProgressMonitor pm) throws JavaModelException {
		try{
			pm.beginTask("Creating change", 1);
			CompositeChange builder= new CompositeChange("Extract Interface");
			builder.addAll(fChangeManager.getAllChanges());
			builder.add(createExtractedInterface());
			return builder;	
		} catch(CoreException e){
			throw new JavaModelException(e);
		}	
	}

	private IChange createExtractedInterface() throws CoreException {
		String lineSeparator= getLineSeperator(); 
		IPath cuPath= ResourceUtil.getFile(fInputClass.getCompilationUnit()).getFullPath();
		IPath interfaceCuPath= cuPath
										.removeLastSegments(1)
										.append(fNewInterfaceName + ".java");
		ICompilationUnit newCu= getInputClassPackage().getCompilationUnit(fNewInterfaceName + ".java");
		String source= createExtractedInterfaceCUSource(newCu);
		String formattedSource= ToolFactory.createCodeFormatter().format(source, 0, null, lineSeparator);
		return new CreateTextFileChange(interfaceCuPath, formattedSource);	
	}
	
	private static String getLineSeperator() {
		return System.getProperty("line.separator", "\n");//$NON-NLS-1$ //$NON-NLS-2$
	}
	private IPackageFragment getInputClassPackage() {
		return (IPackageFragment)fInputClass.getCompilationUnit().getParent();
	}

	private String createExtractedInterfaceCUSource(ICompilationUnit newCu) throws CoreException {
		StringBuffer buffer= new StringBuffer();
		if (fCodeGenerationSettings.createFileComments)
			buffer.append(createFileCommentsSource(newCu));
		buffer.append(createPackageDeclarationSource());
		buffer.append(createImportsSource());
		if (fCodeGenerationSettings.createComments){
			buffer.append(getLineSeperator());
			buffer.append(createTypeCommentSource(newCu));
		}	
		buffer.append(createInterfaceSource());
		return buffer.toString();
	}

	private String createInterfaceSource() throws JavaModelException {
		StringBuffer buff= new StringBuffer();
		buff.append("interface ")
			 .append(fNewInterfaceName)
			 .append(" {")
			 .append(createInterfaceMemberDeclarationsSource())
			 .append("}");
		return buff.toString();
	}

	private String createInterfaceMemberDeclarationsSource() throws JavaModelException {
		StringBuffer buff= new StringBuffer();
		for (int i= 0; i < fExtractedMembers.length; i++) {
			buff.append(createInterfaceMemberDeclarationsSource(fExtractedMembers[i]));
		}
		return buff.toString();
	}

	private String createInterfaceMemberDeclarationsSource(IMember iMember) throws JavaModelException {
		Assert.isTrue(iMember.getElementType() == IJavaElement.FIELD || iMember.getElementType() == IJavaElement.METHOD);
		if (iMember.getElementType() == IJavaElement.FIELD)
			return iMember.getSource();
		else 
			return createInterfaceMethodDeclarationsSource((IMethod)iMember);
	}

	private String createInterfaceMethodDeclarationsSource(IMethod iMethod) throws JavaModelException {
		MethodDeclaration methodDeclaration= getMethodDeclarationNode(iMethod, false);
		if (methodDeclaration == null)
			return ""; 

		String methodDeclarationSource= iMethod.getSource().substring(getDeclarationRelativeStartPosition(methodDeclaration), getDeclarationRelativeEndPosition(methodDeclaration));
		if (methodDeclaration.getBody() == null)
			return methodDeclarationSource;
		else
			return methodDeclarationSource + ";";
	}

	private static int getDeclarationRelativeStartPosition(MethodDeclaration methodDeclaration) {
		//without modifiers	
		return methodDeclaration.getReturnType().getStartPosition() - methodDeclaration.getStartPosition();
	}
	
	private static int getDeclarationRelativeEndPosition(MethodDeclaration methodDeclaration) {
		if (methodDeclaration.getBody() == null)
			return methodDeclaration.getStartPosition() + methodDeclaration.getLength() - methodDeclaration.getStartPosition();
		else
			return methodDeclaration.getBody().getStartPosition() - methodDeclaration.getStartPosition();
	}

	private MethodDeclaration getMethodDeclarationNode(IMethod iMethod, boolean resolveBindings) throws JavaModelException{
		SelectionAnalyzer analyzer= new SelectionAnalyzer(Selection.createFromStartLength(iMethod.getSourceRange().getOffset(), iMethod.getSourceRange().getLength()), true);
		AST.parseCompilationUnit(fInputClass.getCompilationUnit(), resolveBindings).accept(analyzer);
		if (! (analyzer.getFirstSelectedNode() instanceof MethodDeclaration))
			return null; //???
		return (MethodDeclaration) analyzer.getFirstSelectedNode();
	}
	
	private String createImportsSource() throws JavaModelException {
		StringBuffer buff= new StringBuffer();
		ITypeBinding[] typesUsed= getTypesUsedInExtractedMemberDeclarations();
		for (int i= 0; i < typesUsed.length; i++) {
			ITypeBinding binding= typesUsed[i];
			if (binding != null && ! binding.isPrimitive()){
				buff.append("import ");//$NON-NLS-1$
				buff.append(Bindings.getFullyQualifiedImportName(binding));
				buff.append(";");//$NON-NLS-1$
			}	
		}
		return buff.toString();
	}

	private ITypeBinding[] getTypesUsedInExtractedMemberDeclarations() throws JavaModelException{
		Set typesUsed= new HashSet();
		for (int i= 0; i < fExtractedMembers.length; i++) {
			if (fExtractedMembers[i].getElementType() == IJavaElement.METHOD)
				typesUsed.addAll(getTypesUsedInDeclaration((IMethod)fExtractedMembers[i]));
			else if (fExtractedMembers[i].getElementType() == IJavaElement.FIELD)	
				typesUsed.addAll(getTypesUsedInDeclaration((IField)fExtractedMembers[i]));	
			else
				Assert.isTrue(false);	
		}
		return (ITypeBinding[]) typesUsed.toArray(new ITypeBinding[typesUsed.size()]);
	}
	
	//set of ITypeBindings
	private Set getTypesUsedInDeclaration(IField iField) {
		//XXX
		return new HashSet(0);
	}

	//set of ITypeBindings
	private Set getTypesUsedInDeclaration(IMethod iMethod) throws JavaModelException {
		MethodDeclaration methodDeclaration= getMethodDeclarationNode(iMethod, true);
		if (methodDeclaration == null)
			return new HashSet(0);
		Set result= new HashSet();	
		result.add(methodDeclaration.getReturnType().resolveBinding());
			
		for (Iterator iter= methodDeclaration.parameters().iterator(); iter.hasNext();) {
			result.add(((SingleVariableDeclaration) iter.next()).getType().resolveBinding()); 
		}
		
		for (Iterator iter= methodDeclaration.thrownExceptions().iterator(); iter.hasNext();) {
			result.add(((Name) iter.next()).resolveTypeBinding());
		}
		return result;
	}

	private String createPackageDeclarationSource() {
		return "package " + getInputClassPackage().getElementName() + ";";//$NON-NLS-2$ //$NON-NLS-1$
	}

	private String createTypeCommentSource(ICompilationUnit newCu) throws CoreException {
		return getTemplate("typecomment", 0, newCu);//$NON-NLS-1$
	}

	private String createFileCommentsSource(ICompilationUnit newCu) throws CoreException {
		return getTemplate("filecomment", 0, newCu);//$NON-NLS-1$
	}

	private static String getTemplate(String name, int pos, ICompilationUnit newCu) throws CoreException {
		Template[] templates= Templates.getInstance().getTemplates(name);
		if (templates.length == 0)
			return ""; //$NON-NLS-1$	
		String template= JavaContext.evaluateTemplate(templates[0], newCu, pos);
		if (template == null)
			return ""; //$NON-NLS-1$
		return template;
	}

	private TextChangeManager createChangeManager(IProgressMonitor pm) throws CoreException{
		try{
			pm.beginTask("", 2); //$NON-NLS-1$
			TextChangeManager manager= new TextChangeManager();
			updateTypeDeclaration(manager, new SubProgressMonitor(pm, 1));
			if (fReplaceOccurrences)
				updateOccurrences(manager, new SubProgressMonitor(pm, 1));
			else
				pm.worked(1);	
			return manager;
		} finally{
			pm.done();
		}	
	}

	private void updateOccurrences(TextChangeManager manager, IProgressMonitor pm) {
		pm.beginTask("", 1);//$NON-NLS-1$
		pm.done();
	}

	private void updateTypeDeclaration(TextChangeManager manager, IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 1);//$NON-NLS-1$
		String editName= "";
		int offset= computeIndexOfSuperinterfaceNameInsertion();
		String text=  computeTextOfSuperinterfaceNameInsertion();
		ICompilationUnit cu= WorkingCopyUtil.getWorkingCopyIfExists(fInputClass.getCompilationUnit());
		manager.get(cu).addTextEdit(editName, SimpleTextEdit.createInsert(offset, text));
		pm.done();
	}

	private String computeTextOfSuperinterfaceNameInsertion() throws JavaModelException {
		if (! inputClassHasDirectSuperinterfaces())
			return "implements " + fNewInterfaceName;	//$NON-NLS-1$
		else
			return ", " + fNewInterfaceName; //$NON-NLS-1$
	}

	private int computeIndexOfSuperinterfaceNameInsertion() throws JavaModelException {
		TypeDeclaration typeNode= getTypeDeclarationNode();
		Name superClassName= typeNode.getSuperclass();
		List superInterfaces= typeNode.superInterfaces();
		if (superInterfaces.isEmpty()){
			if (superClassName == null)
				return typeNode.getName().getStartPosition() + typeNode.getName().getLength() + 1;
			else 
				return superClassName.getStartPosition() + superClassName.getLength() + 1;
		} else {
			Name lastInterfaceName= (Name)superInterfaces.get(superInterfaces.size() - 1);
			return lastInterfaceName.getStartPosition() + lastInterfaceName.getLength() + 1;
		}
	}

	private TypeDeclaration getTypeDeclarationNode() throws JavaModelException {
		SelectionAnalyzer analyzer= new SelectionAnalyzer(Selection.createFromStartLength(fInputClass.getNameRange().getOffset(), fInputClass.getNameRange().getLength() +1), true);
		CompilationUnit cuNode= AST.parseCompilationUnit(fInputClass.getCompilationUnit(), false);
		cuNode.accept(analyzer);
		if (analyzer.getFirstSelectedNode() != null){
			if (analyzer.getFirstSelectedNode().getParent() instanceof TypeDeclaration)
				return (TypeDeclaration)analyzer.getFirstSelectedNode().getParent();
		}
		//XXX workaround for 21757
		if (analyzer.getLastCoveringNode() instanceof TypeDeclaration)
			return (TypeDeclaration) analyzer.getLastCoveringNode();
		return null;	
	}

	
	private boolean inputClassHasDirectSuperinterfaces() throws JavaModelException {
		return fInputClass.getSuperInterfaceNames().length > 0;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.IRefactoring#getName()
	 */
	public String getName() {
		return "Extract Interface";
	}
	
	public IType getInputClass() {
		return fInputClass;
	}

	public String getNewInterfaceName() {
		return fNewInterfaceName;
	}

	public boolean isReplaceOccurrences() {
		return fReplaceOccurrences;
	}

	public void setNewInterfaceName(String newInterfaceName) {
		Assert.isNotNull(newInterfaceName);
		fNewInterfaceName= newInterfaceName;
	}

	public void setReplaceOccurrences(boolean replaceOccurrences) {
		fReplaceOccurrences= replaceOccurrences;
	}
	
	public void setExtractedMembers(IMember[] extractedMembers) throws JavaModelException{
		Assert.isTrue(areAllExtractableMembersOfClass(extractedMembers));
		fExtractedMembers= extractedMembers;
	}
	
	public IMember[] getExtractableMembers() throws JavaModelException{
		List members= new ArrayList();
		IJavaElement[] children= fInputClass.getChildren();
		for (int i= 0; i < children.length; i++) {
			if (children[i] instanceof IMember && isExtractableMember((IMember)children[i]))
				members.add(children[i]);
		}
		return (IMember[]) members.toArray(new IMember[members.size()]);
	}
	
	private boolean areAllExtractableMembersOfClass(IMember[] extractedMembers) throws JavaModelException {
		for (int i= 0; i < extractedMembers.length; i++) {
			if (! extractedMembers[i].getParent().equals(fInputClass))
				return false;
			if (! isExtractableMember(extractedMembers[i]))
				return false;
		}
		return true;
	}

	private static boolean isExtractableMember(IMember iMember) throws JavaModelException {
		if (iMember.getElementType() == IJavaElement.METHOD)
			return isExtractableMethod((IMethod)iMember);
		if (iMember.getElementType() == IJavaElement.FIELD)	
			return isExtractableField((IField)iMember);
		return false;	
	}

	private static boolean isExtractableField(IField iField) throws JavaModelException {
		if (! Flags.isPublic(iField.getFlags()))
			return false;
		if (! Flags.isStatic(iField.getFlags()))
			return false;
		if (! Flags.isFinal(iField.getFlags()))
			return false;
		return true;		
	}

	private static boolean isExtractableMethod(IMethod iMethod) throws JavaModelException {
		if (! Flags.isPublic(iMethod.getFlags()))
			return false;
		if (Flags.isStatic(iMethod.getFlags()))
			return false;
		return true;		
	}
}
