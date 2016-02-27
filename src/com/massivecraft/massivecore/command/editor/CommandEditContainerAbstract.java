package com.massivecraft.massivecore.command.editor;

import java.util.AbstractMap.SimpleImmutableEntry;

import org.bukkit.ChatColor;

import java.util.Collection;
import java.util.List;

import com.massivecraft.massivecore.MassiveException;
import com.massivecraft.massivecore.collections.MassiveList;
import com.massivecraft.massivecore.command.requirement.RequirementEditorPropertyCreated;
import com.massivecraft.massivecore.command.type.Type;
import com.massivecraft.massivecore.command.type.TypeNullable;
import com.massivecraft.massivecore.mson.Mson;
import com.massivecraft.massivecore.util.ContainerUtil;
import com.massivecraft.massivecore.util.Txt;

public abstract class CommandEditContainerAbstract<O, V> extends CommandEditAbstract<O, V>
{
	// -------------------------------------------- //
	// CONSTRUCT
	// -------------------------------------------- //
	
	public CommandEditContainerAbstract(EditSettings<O> settings, Property<O, V> property)
	{
		// Super
		super(settings, property, true);
		
		// Aliases
		String alias = this.createCommandAlias();
		this.setAliases(alias);
		
		// Desc
		this.setDesc(alias + " " + this.getPropertyName());
		
		// Requirements
		this.addRequirements(RequirementEditorPropertyCreated.get(true));
	}
	
	// -------------------------------------------- //
	// OVERRIDE
	// -------------------------------------------- //
	
	@Override
	public void perform() throws MassiveException
	{
		// Create
		V before = this.getProperty().getRaw(this.getObject());
		
		// We must use a container of the correct type.
		// Otherwise the elements list for maps,
		// could have two entries with the same key.
		// That obviously caused issues.
		V container = ContainerUtil.getCopy(before);
		
		// Alter
		try
		{
			this.alter(container);
		}
		catch (MassiveException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new MassiveException().addMsg("<b>%s", e.getMessage());
		}
		
		// After
		V after = this.getValueType().createNewInstance();
		List<Object> elements = this.getValueType().getContainerElementsOrdered(container);
		ContainerUtil.addElements(after, elements);
		
		// Apply
		this.attemptSet(after);
	}
	
	@Override
	public String createCommandAlias()
	{
		// Split at uppercase letters
		String name = this.getClass().getSimpleName();
		name = name.substring("CommandEditContainer".length());
		final String[] words = name.split("(?=[A-Z])");
		String alias = Txt.implode(words, "");
		alias = Txt.lowerCaseFirst(alias);
		return alias;
	}
	
	// -------------------------------------------- //
	// ATTEMPT SET
	// -------------------------------------------- //
	
	@Override
	public Mson attemptSetNochangeMessage()
	{
		return mson(
			this.getProperty().getDisplayNameMson(),
			" for ",
			this.getObjectVisual(),
			" not changed."	
		).color(ChatColor.GRAY);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void attemptSetPerform(V after)
	{
		V before = this.getInheritedValue();
		Mson descProperty = this.getProperty().getDisplayNameMson();
		
		// Apply
		// We set the new property value.
		this.getProperty().setValue(this.getObject(), after);
		
		// Create messages
		List<Mson> messages = new MassiveList<>();
		
		messages.add(mson(
			descProperty,
			mson(" for ").color(ChatColor.GRAY),
			this.getObjectVisual(),
			mson(" edited:").color(ChatColor.GRAY)
		));
		
		// Note: The result of getAdditions is not actually V, but the implementation doesn't care.
		Collection<Object> additions = ContainerUtil.getAdditions(before, after);
		if ( ! additions.isEmpty())
		{
			messages.add(mson(
				mson("Additions: ").color(ChatColor.AQUA),
				this.getValueType().getVisualMson((V) additions)
			));
		}
		
		// Note: The result of getDeletions is not actually V, but the implementation doesn't care.
		Collection<Object> deletions = ContainerUtil.getDeletions(before, after);
		if ( ! deletions.isEmpty())
		{
			messages.add(mson(
				mson("Deletions: ").color(ChatColor.AQUA),
				this.getValueType().getVisualMson((V) deletions)
			));
		}
		
		message(messages);
	}
	
	// -------------------------------------------- //
	// ABSTRACT
	// -------------------------------------------- //
	// Not actually abstract, but one of these must be implemented;
	
	public void alter(V container) throws MassiveException
	{
		List<Object> elements = toElements(container);
		
		this.alterElements(elements);
		
		ContainerUtil.setElements(container, elements);
	}
	
	public void alterElements(List<Object> elements) throws MassiveException
	{
		throw new MassiveException().addMsg("<b>Not yet implemented.");
	}
	
	// -------------------------------------------- //
	// PARAMETER
	// -------------------------------------------- //
	
	public boolean isCollection()
	{
		Type<V> type = this.getValueType();
		if (type.isContainerCollection()) return true;
		if (type.isContainerMap()) return false;
		throw new RuntimeException("Neither Collection nor Map.");
	}
	
	public void addParametersElement(boolean strict)
	{
		Type<Object> innerType = this.getValueInnerType();
		
		if (this.isCollection())
		{
			this.addParameter(innerType, true);
		}
		else
		{
			Type<Object> keyType = innerType.getInnerType(innerType.getIndexUser(0));
			Type<Object> valueType = innerType.getInnerType(innerType.getIndexUser(1));
			if (strict)
			{
				this.addParameter(keyType);
				this.addParameter(valueType, true);
			}
			else
			{
				this.addParameter(null, TypeNullable.get(keyType, "any", "all"), keyType.getTypeName(), "any");
				this.addParameter(null, TypeNullable.get(valueType, "any", "all"), valueType.getTypeName(), "any", true);
			}
		}
	}
	
	public Object readElement() throws MassiveException
	{
		if (this.isCollection())
		{
			return this.readArg();
		}
		else
		{
			Object key = this.readArgAt(this.getValueInnerType().getIndexTech(0));
			Object value = this.readArgAt(this.getValueInnerType().getIndexTech(1));
			return new SimpleImmutableEntry<Object, Object>(key, value);
		}
	}
	
	// -------------------------------------------- //
	// ELEMENTS UTIL
	// -------------------------------------------- //

	public List<Object> toElements(V container)
	{
		return this.getValueType().getContainerElementsOrdered(container);
	}
}