package org.beetl.core.engine;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.beetl.core.Context;
import org.beetl.core.InferContext;
import org.beetl.core.statement.Program;
import org.beetl.core.statement.ProgramMetaData;
import org.beetl.core.statement.Statement;
import org.beetl.core.statement.Type;

/**
 * 内置fitler，对模板进行类型推测
 * 
 * @author joelli
 * 
 */
public class TypeBindingProbe extends Probe
{
	boolean isCompleted = false;
	Type[] types = null;
	ProgramMetaData copyProgramMetaData = null;
	Probe nextFilter = null;

	public TypeBindingProbe(Program p, Probe nextFilter)
	{
		super(p);
		// 一个新的copy，用于分析
		ProgramMetaData metaData = p.metaData.copy();
		Program copyProgram = new Program();
		copyProgram.metaData = metaData;
		copyProgram.id = p.id;
		copyProgram.gt = p.gt;
		copyProgram.rs = p.rs;
		this.program = copyProgram;

		nextFilter.program = this.program;
		this.nextFilter = nextFilter;
		types = new Type[program.metaData.varIndexSize];
		//对type类型做设定
		if (metaData.allDynamic)
		{
			for (int i = 0; i < types.length; i++)
			{
				types[i] = Type.ObjectType;
			}
		}

		if (metaData.dynamicObjectSet.size() != 0)
		{
			for (String varName : metaData.dynamicObjectSet)
			{
				Integer index = metaData.globalIndexMap.get(varName);
				if (index != null)
				{
					types[index] = Type.ObjectType;
				}

			}
		}

		if (metaData.globalType.size() != 0)
		{
			for (Entry<String, Type> entry : metaData.globalType.entrySet())
			{
				String varName = entry.getKey();
				Type type = entry.getValue();
				Integer index = metaData.globalIndexMap.get(varName);
				if (index != null)
				{
					types[index] = type;
				}
				//不抛错，允许这种情况出现
			}
		}

	}

	@Override
	public void check(Context ctx)
	{
		if (isCompleted)
			return;

		int y = 0;
		for (int i = 0; i < program.metaData.tempVarStartIndex; i++)
		{
			if (types[i] == null)
			{
				if (ctx.vars[i] != ctx.NOT_EXIST_OBJECT)
				{
					Object o = ctx.vars[i];
					Type c = getType(o);
					if (c == null)
						continue;
					else
					{
						types[i] = c;
						y++;
					}
				}
				else
				{
					continue;
				}

			}
			else
			{
				y++;
			}
		}
		// 推测完毕
		if (y == program.metaData.tempVarStartIndex)
		{
			infer();
			isCompleted = true;
			// 调用下一个filter
			nextFilter.check(ctx);

		}
	}

	/**
	 * 确定表达式的类型
	 */
	protected void infer()
	{

		InferContext ctx = new InferContext();
		ctx.types = types;
		ctx.gt = this.program.gt;
		for (Statement st : this.program.metaData.statements)
		{
			st.infer(ctx);
		}

	}

	private Type getType(Object c)
	{
		Type type = null;
		;
		if (c instanceof Map)
		{
			Map<Object, Object> map = (Map<Object, Object>) c;
			for (Entry<Object, Object> entry : map.entrySet())
			{

				Object key = entry.getKey();
				Object value = entry.getValue();
				if (value != null)
				{
					type = new Type(Map.class, key.getClass(), value.getClass());
					return type;
				}

			}
			// 没有足够信息，还需要推测
			return null;
		}
		else if (c instanceof List)
		{
			List<Object> list = (List<Object>) c;
			for (Object o : list)
			{
				if (o != null)
				{

					type = new Type(List.class, o.getClass());
					return type;
				}
			}
			// 没有足够信息
			return null;
		}
		else if (c.getClass().isArray())
		{
			// Class probableType = c.getClass()
			type = new Type(c.getClass(), c.getClass().getComponentType());
			return type;
		}
		else
		{
			return new Type(c.getClass());
		}
	}
}
