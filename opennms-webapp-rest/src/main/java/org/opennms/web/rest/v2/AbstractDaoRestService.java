/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2008-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.web.rest.v2;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.locks.Lock;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.jaxrs.ext.search.SearchCondition;
import org.apache.cxf.jaxrs.ext.search.SearchConditionVisitor;
import org.apache.cxf.jaxrs.ext.search.SearchContext;
import org.opennms.core.criteria.Criteria;
import org.opennms.core.criteria.CriteriaBuilder;
import org.opennms.netmgt.dao.api.OnmsDao;
import org.opennms.web.rest.support.CriteriaBuilderSearchVisitor;
import org.opennms.web.rest.support.MultivaluedMapImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import com.googlecode.concurentlocks.ReadWriteUpdateLock;
import com.googlecode.concurentlocks.ReentrantReadWriteUpdateLock;

public abstract class AbstractDaoRestService<T,K extends Serializable> {
	private static final Logger LOG = LoggerFactory.getLogger(AbstractDaoRestService.class);

	private final ReadWriteUpdateLock m_globalLock = new ReentrantReadWriteUpdateLock();
	private final Lock m_writeLock = m_globalLock.writeLock();

	protected static final int DEFAULT_LIMIT = 10;

	protected abstract OnmsDao<T,K> getDao();
	protected abstract Class<T> getDaoClass();
	protected abstract CriteriaBuilder getCriteriaBuilder();

	protected final void writeLock() {
		m_writeLock.lock();
	}

	protected final void writeUnlock() {
		m_writeLock.unlock();
	}

	protected Criteria getCriteria(UriInfo uriInfo, SearchContext searchContext) {
		final MultivaluedMap<String, String> params = uriInfo.getQueryParameters();

		final CriteriaBuilder builder = getCriteriaBuilder();

		if (searchContext != null) {
			SearchCondition<T> condition = searchContext.getCondition(getDaoClass());
			if (condition != null) {
				SearchConditionVisitor<T,CriteriaBuilder> visitor = new CriteriaBuilderSearchVisitor<T>(builder, getDaoClass());
				condition.accept(visitor);
			}
		}

		// Apply limit, offset, orderBy, order params
		applyLimitOffsetOrderBy(params, builder);

		Criteria crit = builder.toCriteria();

		/*
		TODO: Figure out how to do stuff like this

		// Don't include deleted nodes by default
		final String type = params.getFirst("type");
		if (type == null) {
			final List<Restriction> restrictions = new ArrayList<Restriction>(crit.getRestrictions());
			restrictions.add(Restrictions.ne("type", "D"));
			crit.setRestrictions(restrictions);
		}
		 */
		
		return crit;
	}

	@GET
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.APPLICATION_ATOM_XML})
	public Response get(@Context final UriInfo uriInfo, @Context final SearchContext searchContext) {
		final MultivaluedMap<String, String> params = uriInfo.getQueryParameters();

		final CriteriaBuilder builder = getCriteriaBuilder();

		if (searchContext != null) {
			SearchCondition<T> condition = searchContext.getCondition(getDaoClass());
			if (condition != null) {
				SearchConditionVisitor<T,CriteriaBuilder> visitor = new CriteriaBuilderSearchVisitor<T>(builder, getDaoClass());
				condition.accept(visitor);
			}
		}

		// Apply limit, offset, orderBy, order params
		applyLimitOffsetOrderBy(params, builder);

		Criteria crit = builder.toCriteria();

		/*
		TODO: Figure out how to do stuff like this

		// Don't include deleted nodes by default
		final String type = params.getFirst("type");
		if (type == null) {
			final List<Restriction> restrictions = new ArrayList<Restriction>(crit.getRestrictions());
			restrictions.add(Restrictions.ne("type", "D"));
			crit.setRestrictions(restrictions);
		}
		 */

		final List<T> coll = getDao().findMatching(crit);

		// TODO: Figure out how to encapsulate lists in a wrapper object
		// Remove limit, offset and ordering for count
		/*
		crit.setLimit(null);
		crit.setOffset(null);
		crit.setOrders(new ArrayList<Order>());

		coll.setTotalCount(getDao().countMatching(crit));
		 */

		if (coll == null || coll.size() < 1) {
			return Response.status(Status.NOT_FOUND).build();
		} else {
			return Response.ok(coll).build();
		}
	}

	@GET
	@Path("count")
	@Produces({MediaType.TEXT_PLAIN})
	public Response getCount(@Context final UriInfo uriInfo, @Context final SearchContext searchContext) {
		return Response.ok(String.valueOf(getDao().countMatching(getCriteria(uriInfo, searchContext)))).build();
	}

	@GET
	@Path("{id}")
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.APPLICATION_ATOM_XML})
	public Response get(@PathParam("id") final K id) {
		T retval = getDao().get(id);
		if (retval == null) {
			return Response.status(Status.NOT_FOUND).build();
		} else {
			return Response.ok(retval).build();
		}
	}

	@POST
	@Path("{id}")
	public Response createSpecific() {
		// Return a 404 if somebody tries to create with a specific ID
		return Response.status(Status.NOT_FOUND).build();
	}

	@POST
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public Response create(@Context final UriInfo uriInfo, T object) {
		K id = getDao().save(object);
		return Response.created(getRedirectUri(uriInfo, id)).build();
	}

	@PUT
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public Response updateMany(@Context final UriInfo uriInfo, @Context final SearchContext searchContext, final MultivaluedMapImpl params) {
		// TODO: Implement me
		return Response.status(Status.NOT_IMPLEMENTED).build();
	}

	@PUT
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Path("{id}")
	public Response update(@Context final UriInfo uriInfo, @PathParam("id") final K id, final MultivaluedMapImpl params) {
		writeLock();

		try {
			final T object = getDao().get(id);

			if (object == null) {
				return Response.status(Status.NOT_FOUND).build();
			}

			LOG.debug("update: updating object {}", object);

			final BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(object);
			for(final String key : params.keySet()) {
				if (wrapper.isWritableProperty(key)) {
					final String stringValue = params.getFirst(key);
					final Object value = wrapper.convertIfNecessary(stringValue, (Class<?>)wrapper.getPropertyType(key));
					wrapper.setPropertyValue(key, value);
				}
			}

			LOG.debug("update: object {} updated", object);
			getDao().saveOrUpdate(object);
			return Response.noContent().build();
		} finally {
			writeUnlock();
		}
	}

	@DELETE
	public Response deleteMany(@Context final UriInfo uriInfo, @Context final SearchContext searchContext) {
		writeLock();

		try {
			List<T> objects = (List<T>)get(uriInfo, searchContext).getEntity();

			if (objects == null || objects.size() == 0) {
				return Response.status(Status.NOT_FOUND).build();
			}

			for (T object : objects) {
				LOG.debug("delete: deleting object {}", object);
				getDao().delete(object);
			}
			return Response.ok().build();
		} finally {
			writeUnlock();
		}
	}

	@DELETE
	@Path("{id}")
	public Response delete(@PathParam("id") final K criteria) {
		writeLock();

		try {
			final T object = getDao().get(criteria);

			if (object == null) {
				return Response.status(Status.NOT_FOUND).build();
			}

			LOG.debug("delete: deleting object {}", criteria);
			getDao().delete(object);
			return Response.ok().build();
		} finally {
			writeUnlock();
		}
	}

	private static void applyLimitOffsetOrderBy(final MultivaluedMap<String,String> p, final CriteriaBuilder builder) {
		applyLimitOffsetOrderBy(p, builder, DEFAULT_LIMIT);
	}

	private static void applyLimitOffsetOrderBy(final MultivaluedMap<String,String> p, final CriteriaBuilder builder, final Integer defaultLimit) {

		final MultivaluedMap<String, String> params = new MultivaluedMapImpl();
		params.putAll(p);

		builder.distinct();
		builder.limit(defaultLimit);

		if (params.containsKey("limit")) {
			builder.limit(Integer.valueOf(params.getFirst("limit")));
			params.remove("limit");
		}

		if (params.containsKey("offset")) {
			builder.offset(Integer.valueOf(params.getFirst("offset")));
			params.remove("offset");
		}

		if(params.containsKey("orderBy")) {
			builder.orderBy(params.getFirst("orderBy"));
			params.remove("orderBy");

			if(params.containsKey("order")) {
				if("desc".equalsIgnoreCase(params.getFirst("order"))) {
					builder.desc();
				} else {
					builder.asc();
				}
				params.remove("order");
			}
		}
	}

	private static URI getRedirectUri(final UriInfo uriInfo, final Object... pathComponents) {
		if (pathComponents != null && pathComponents.length == 0) {
			final URI requestUri = uriInfo.getRequestUri();
			try {
				return new URI(requestUri.getScheme(), requestUri.getUserInfo(), requestUri.getHost(), requestUri.getPort(), requestUri.getPath().replaceAll("/$", ""), null, null);
			} catch (final URISyntaxException e) {
				return requestUri;
			}
		} else {
			UriBuilder builder = uriInfo.getRequestUriBuilder();
			for (final Object component : pathComponents) {
				if (component != null) {
					builder = builder.path(component.toString());
				}
			}
			return builder.build();
		}
	}
}
